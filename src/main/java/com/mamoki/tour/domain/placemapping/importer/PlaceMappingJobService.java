package com.mamoki.tour.domain.placemapping.importer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.placemapping.PlaceMappingProperties;
import com.mamoki.tour.domain.placemapping.entity.PlaceMapping;
import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.placemapping.enums.PlaceMappingStatus;
import com.mamoki.tour.domain.placemapping.repository.PlaceMappingRepository;
import com.mamoki.tour.domain.placemapping.support.CatalogCandidate;
import com.mamoki.tour.domain.placemapping.support.NameVariantIndex;
import com.mamoki.tour.domain.placemapping.support.NameVariantOutcome;
import com.mamoki.tour.domain.placemapping.support.OutOfCatalogRules;
import com.mamoki.tour.domain.placemapping.support.PlaceMappingDecider;
import com.mamoki.tour.domain.placemapping.support.PlaceMappingDecision;
import com.mamoki.tour.domain.placemapping.support.UnmatchedClassifier;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.kakao.KakaoAuthenticationException;
import com.mamoki.tour.infra.kakao.KakaoLocalClient;
import com.mamoki.tour.infra.kakao.KakaoRateLimitException;
import com.mamoki.tour.infra.kakao.dto.KakaoPlace;

/**
 * 카탈로그에 잇지 못한 원천 이름을 카카오로 찾아 매핑 표에 남기고, 남은 것을 분류한다.
 *
 * <p><b>이 배치는 조회 응답을 바꾸지 않는다.</b> 판정을 표에 남길 뿐이고, TMAP·입장객은
 * 다음 적재가 그 표를 읽어 이어 붙인다. 스냅샷은 그 적재가 무엇을 만들었는지의 기록이라
 * 나중에 고쳐 넣으면 그 뜻이 무너진다. 연관 장소는 조회 시점에 매핑을 보므로 바로 반영된다.
 *
 * <p>멈춰야 할 때 멈춘다. 인증 실패와 한도 초과는 남은 이름을 계속 불러도 모두 같은 답을
 * 받는 상황이라 그 자리에서 끝낸다. 그때까지 판정한 것은 이름마다 따로 커밋되어 남는다.
 *
 * <h2>판정 뒤의 분류 (#72)</h2>
 * 카카오로도 좁히지 못한 이름을 세 갈래로 나눈다. 표기 차이로 카탈로그를 되찾을 수 있으면
 * 확정으로 올리고, 카탈로그가 애초에 담지 않는 종류면 분모에서 뺀다. 나머지는 모른다고
 * 남겨 분모에 둔다. 요약 로그는 <b>분모를 정리하기 전과 후의 매칭률을 함께</b> 찍는다.
 * 정리한 뒤 수치만 남기면 더 이어서 오른 것인지 분모를 줄여서 오른 것인지 알 수 없다.
 */
@Service
public class PlaceMappingJobService {

    private static final Logger log = LoggerFactory.getLogger(PlaceMappingJobService.class);

    private final KakaoLocalClient kakaoLocalClient;
    private final PlaceMappingWriter writer;
    private final PlaceMappingRepository placeMappingRepository;
    private final AttractionRepository attractionRepository;
    private final PlaceMappingDecider decider;
    private final PlaceMappingProperties properties;
    private final Map<MappingSource, UnmatchedNameCollector> collectors;

    public PlaceMappingJobService(KakaoLocalClient kakaoLocalClient,
                                  PlaceMappingWriter writer,
                                  PlaceMappingRepository placeMappingRepository,
                                  AttractionRepository attractionRepository,
                                  PlaceMappingProperties properties,
                                  List<UnmatchedNameCollector> collectors) {
        this.kakaoLocalClient = kakaoLocalClient;
        this.writer = writer;
        this.placeMappingRepository = placeMappingRepository;
        this.attractionRepository = attractionRepository;
        this.properties = properties;
        this.decider = new PlaceMappingDecider(properties.boundaryMeters(),
                properties.nameBoundaryMultiplier());
        this.collectors = new LinkedHashMap<>();

        for (UnmatchedNameCollector collector : collectors) {
            this.collectors.put(collector.source(), collector);
        }
    }

    public PlaceMappingResult run(MappingSource source) {
        if (!kakaoLocalClient.hasCredentials()) {
            // 키 없이 돌면 모든 호출이 401 이다. 한 번도 부르지 않고 이유를 남기고 끝낸다.
            log.error("KAKAO_REST_API_KEY 가 비어 있어 장소 매핑을 시작하지 않습니다. "
                    + ".env 에 REST API 키를 넣으세요.");

            return PlaceMappingResult.notRun(source, "KAKAO_REST_API_KEY 없음");
        }

        UnmatchedNameCollector collector = collectors.get(source);

        if (collector == null) {
            return PlaceMappingResult.notRun(source, "원천을 모을 수 없음");
        }

        CollectedNames collected = collector.collect();

        log.info("{} 원천의 미매칭 이름 {}건을 판정합니다. 전체 {}행 중 {}행이 이미 이어져 있습니다. "
                        + "거리 경계={}m, 이름 경로 상한={}m",
                source.optionValue(), collected.names().size(), collected.totalRows(),
                collected.matchedRows(), decider.boundaryMeters(), decider.nameBoundaryMeters());

        return decideAll(source, collected);
    }

    private PlaceMappingResult decideAll(MappingSource source, CollectedNames collected) {
        List<UnmatchedPlaceName> names = collected.names();
        Map<String, PlaceMapping> existing = existingByKey(source);
        Map<String, RegionCatalog> catalogs = loadRegions(names);

        UnmatchedClassifier classifier = new UnmatchedClassifier(catalogAwareRules());
        Map<String, NameVariantOutcome> variants =
                resolveNameVariants(names, catalogs, takenContentIds(collected, existing));

        int skipped = 0;
        int calls = 0;
        int confirmed = 0;
        int lowConfidence = 0;
        int unmatched = 0;
        int nameVariant = 0;
        int outOfCatalog = 0;
        int unknown = 0;
        String stoppedReason = null;

        for (UnmatchedPlaceName name : names) {
            if (isSettled(existing.get(name.key()))) {
                skipped++;
                continue;
            }

            if (calls >= properties.maxCallsPerRun()) {
                stoppedReason = "한 실행의 호출 상한(%d)에 도달".formatted(properties.maxCallsPerRun());
                break;
            }

            RegionCatalog catalog = catalogs.get(name.lawdCode());

            List<KakaoPlace> places;
            try {
                // 세는 것은 "부르려고 한 횟수" 다. 실패한 호출도 한도를 깎으므로 실패 경로에서
                // 빠지면 보고된 호출 수가 실제보다 적어진다.
                calls++;
                places = search(name, catalog);
            } catch (KakaoAuthenticationException e) {
                stoppedReason = "카카오 인증 실패";
                log.error("카카오 인증에 실패해 장소 매핑을 멈춥니다. 남은 이름은 다음 실행이 봅니다.", e);
                break;
            } catch (KakaoRateLimitException e) {
                stoppedReason = "카카오 호출 한도 초과";
                log.error("카카오 호출 한도를 넘어 장소 매핑을 멈춥니다. 남은 이름은 다음 실행이 봅니다.", e);
                break;
            } catch (ExternalApiException e) {
                // 이 이름 하나의 실패다. 판정을 남기지 않고 다음 이름으로 간다. 값을 지어내지 않는다.
                //
                // 간격은 여기서도 지킨다. 카카오가 계속 500 을 주는 동안 간격 없이 달리면
                // 한 실행의 호출 상한까지 전속력으로 부르고, 그것이 곧 다음 원천의 한도를 깎는다.
                log.warn("카카오 검색에 실패해 이 이름은 건너뜁니다: {} ({})",
                        name.sourceName(), name.regionName(), e);
                sleepBetweenCalls();
                continue;
            }

            PlaceMappingDecision decision = classifier.classify(
                    decider.decide(name.sourceName(), name.regionName(),
                            places, catalog.candidates()),
                    name.sourceName(),
                    variants.getOrDefault(name.key(), NameVariantOutcome.NONE));

            if (!writer.record(source, name, decision, LocalDateTime.now())) {
                skipped++;
                sleepBetweenCalls();
                continue;
            }

            switch (decision.status()) {
                case CONFIRMED -> confirmed++;
                case LOW_CONFIDENCE -> lowConfidence++;
                case UNMATCHED -> unmatched++;
            }

            if (decision.category() != null) {
                switch (decision.category()) {
                    case NAME_VARIANT -> nameVariant++;
                    case OUT_OF_CATALOG -> outOfCatalog++;
                    case UNKNOWN -> unknown++;
                }
            }

            sleepBetweenCalls();
        }

        SourceMatchRate matchRate = matchRate(source, collected);

        PlaceMappingResult result = new PlaceMappingResult(source, names.size(), skipped, calls,
                confirmed, lowConfidence, unmatched, nameVariant, outOfCatalog, unknown,
                matchRate, stoppedReason);

        log.info("장소 매핑 완료: source={}, {}", source.optionValue(), result.summary());
        log.info("장소 매핑 매칭률: source={}, {}", source.optionValue(), matchRate.summary());

        if (confirmed > 0 && source != MappingSource.RELATED_PLACE) {
            // 스냅샷은 그 적재가 무엇을 만들었는지의 기록이라 여기서 고쳐 넣지 않는다.
            log.info("확정 매핑 {}건을 스냅샷에 반영하려면 --job={} 을 다시 실행하세요.",
                    confirmed, source.optionValue());
        }

        return result;
    }

    /**
     * 사전에서 카탈로그가 실제로 담는 종류를 뺀다.
     *
     * <p>카탈로그가 담고 있는 종류를 "카탈로그 대상이 아니다" 라고 말하면, 분모에서 빠진
     * 만큼 매칭률이 거짓으로 올라간다. 무엇이 꺼졌는지는 로그에 남긴다 — 조용히 꺼지면
     * 사전을 고쳐도 아무 일이 없는 이유를 아무도 모른다.
     */
    private OutOfCatalogRules catalogAwareRules() {
        OutOfCatalogRules rules =
                OutOfCatalogRules.load().withCatalogEvidence(attractionRepository.findAllNames());

        if (!rules.suppressedSuffixes().isEmpty()) {
            log.info("카탈로그가 담고 있어 이번 실행에서 끈 접미어 규칙: {}. "
                            + "이 종류는 미매칭이어도 분모에서 빼지 않습니다.",
                    rules.suppressedSuffixes());
        }

        return rules;
    }

    /**
     * 이 원천이 이미 값을 붙인 카탈로그 식별자.
     *
     * <p>표기 차이로 되찾을 때 이미 값이 붙은 관광지에 두 번째 행을 잇지 않으려고 쓴다.
     * 한 관광지에 두 행이 붙으면 조회가 둘 중 아무거나 보여 주는데 에러가 나지 않는다(#84).
     */
    private static Set<String> takenContentIds(CollectedNames collected,
                                               Map<String, PlaceMapping> existing) {
        Set<String> taken = new HashSet<>(collected.matchedContentIds());

        for (PlaceMapping mapping : existing.values()) {
            if (mapping.isUsable()) {
                taken.add(mapping.getContentId());
            }
        }

        return taken;
    }

    /**
     * 표기만 걷어내 카탈로그를 되찾아 본다. 카카오를 부르지 않는다.
     *
     * <p>되찾은 곳이 한 곳이어도 확정하지 않는 경우가 둘 더 있다. 같은 카탈로그를 가리키는
     * 원천 이름이 둘 이상일 때, 그리고 그 카탈로그에 이미 이 원천의 다른 행이 붙어 있을 때다.
     * 둘 다 한 관광지에 값 두 개가 붙는 길이고, 조회는 그중 아무거나 보여 준다.
     */
    private static Map<String, NameVariantOutcome> resolveNameVariants(
            List<UnmatchedPlaceName> names, Map<String, RegionCatalog> catalogs,
            Set<String> taken) {

        Map<String, List<CatalogCandidate>> candidatesByKey = new LinkedHashMap<>();
        Map<String, List<String>> keysByContentId = new LinkedHashMap<>();

        for (UnmatchedPlaceName name : names) {
            RegionCatalog catalog = catalogs.get(name.lawdCode());

            if (catalog == null) {
                continue;
            }

            List<CatalogCandidate> candidates =
                    catalog.variantIndex().candidatesFor(name.sourceName());

            if (candidates.isEmpty()) {
                continue;
            }

            candidatesByKey.put(name.key(), candidates);

            if (candidates.size() == 1) {
                keysByContentId
                        .computeIfAbsent(candidates.get(0).contentId(), key -> new ArrayList<>())
                        .add(name.key());
            }
        }

        Map<String, NameVariantOutcome> outcomes = new HashMap<>();

        candidatesByKey.forEach((key, candidates) -> {
            if (candidates.size() > 1) {
                outcomes.put(key, NameVariantOutcome.blocked(
                        "표기를 걷어내면 같아지는 카탈로그가 %d 곳입니다: %s"
                                .formatted(candidates.size(),
                                        candidates.stream().map(CatalogCandidate::name).toList())));
                return;
            }

            CatalogCandidate target = candidates.get(0);

            if (keysByContentId.get(target.contentId()).size() > 1) {
                outcomes.put(key, NameVariantOutcome.blocked(
                        "카탈로그 %s 를 표기 차이로 가리키는 원천 이름이 %d 개입니다."
                                .formatted(target.name(),
                                        keysByContentId.get(target.contentId()).size())));
                return;
            }

            if (taken.contains(target.contentId())) {
                outcomes.put(key, NameVariantOutcome.blocked(
                        "카탈로그 %s 에는 이 원천의 다른 행이 이미 이어져 있습니다."
                                .formatted(target.name())));
                return;
            }

            outcomes.put(key, NameVariantOutcome.unique(target));
        });

        return outcomes;
    }

    /**
     * 판정을 마친 표를 다시 읽어 매칭률을 낸다.
     *
     * <p>이번 실행이 확정한 것만 세지 않는다. 지난 실행이 확정해 이번에 건너뛴 이름도
     * 분자에 있어야 실제 상태가 된다.
     */
    private SourceMatchRate matchRate(MappingSource source, CollectedNames collected) {
        Map<String, PlaceMapping> decided = existingByKey(source);

        int matchedRows = collected.matchedRows();
        int outOfCatalogRows = 0;

        for (Map.Entry<String, Integer> entry : collected.unmatchedRowsByKey().entrySet()) {
            PlaceMapping mapping = decided.get(entry.getKey());

            if (mapping == null) {
                continue;
            }

            if (mapping.isUsable()) {
                matchedRows += entry.getValue();
            } else if (mapping.isOutOfCatalog()) {
                outOfCatalogRows += entry.getValue();
            }
        }

        return new SourceMatchRate(collected.totalRows(), matchedRows, outOfCatalogRows);
    }

    /** 이미 확정했거나 운영자가 넣은 행은 다시 부르지 않는다. 부르면 한도만 깎는다. */
    private static boolean isSettled(PlaceMapping mapping) {
        return mapping != null
                && (mapping.getStatus() == PlaceMappingStatus.CONFIRMED || mapping.isManual());
    }

    private Map<String, PlaceMapping> existingByKey(MappingSource source) {
        Map<String, PlaceMapping> byKey = new HashMap<>();

        for (PlaceMapping mapping : placeMappingRepository.findAllBySource(source)) {
            byKey.put(mapping.getLawdCode() + "|" + mapping.getNormalizedName(), mapping);
        }

        return byKey;
    }

    private List<KakaoPlace> search(UnmatchedPlaceName name, RegionCatalog catalog) {
        return kakaoLocalClient
                .searchKeyword(name.sourceName(), catalog.latitude(), catalog.longitude())
                .documents();
    }

    private void sleepBetweenCalls() {
        Duration delay = kakaoLocalClient.callDelay();

        if (delay == null || delay.isZero() || delay.isNegative()) {
            return;
        }

        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("장소 매핑이 중단되었습니다.", e);
        }
    }

    /**
     * 한 시·군의 카탈로그 후보와 검색 중심 좌표.
     *
     * <p>중심은 그 시·군 카탈로그 좌표들의 외곽 상자 가운데다. 시·군 중심 좌표를 따로
     * 시드로 두지 않는 것은, 우리가 실제로 이을 대상이 카탈로그이지 행정 경계가 아니기
     * 때문이다. 좌표가 한 곳도 없는 시·군은 중심 없이 이름만으로 찾는다.
     */
    private record RegionCatalog(List<CatalogCandidate> candidates,
                                 BigDecimal latitude, BigDecimal longitude,
                                 NameVariantIndex variantIndex) {
    }

    /**
     * 대상 이름이 걸린 시·군의 카탈로그를 한 번에 읽는다.
     *
     * <p>표기 차이 판정이 "같은 카탈로그를 가리키는 이름이 또 있는가" 를 보므로, 이름을
     * 하나씩 보면서 그때그때 읽으면 그 질문에 답할 수 없다.
     */
    private Map<String, RegionCatalog> loadRegions(List<UnmatchedPlaceName> names) {
        Map<String, RegionCatalog> catalogs = new LinkedHashMap<>();

        for (UnmatchedPlaceName name : names) {
            catalogs.computeIfAbsent(name.lawdCode(),
                    lawdCode -> loadRegion(lawdCode, name.regionName()));
        }

        return catalogs;
    }

    private RegionCatalog loadRegion(String lawdCode, String regionName) {
        List<CatalogCandidate> candidates = new ArrayList<>();

        for (Attraction attraction : attractionRepository.findAllByRegionCodeLawdCode(lawdCode)) {
            candidates.add(new CatalogCandidate(attraction.getContentId(), attraction.getName(),
                    attraction.getLatitude(), attraction.getLongitude()));
        }

        BigDecimal minLatitude = null;
        BigDecimal maxLatitude = null;
        BigDecimal minLongitude = null;
        BigDecimal maxLongitude = null;

        for (CatalogCandidate candidate : candidates) {
            if (!candidate.hasCoordinate()) {
                continue;
            }

            minLatitude = min(minLatitude, candidate.latitude());
            maxLatitude = max(maxLatitude, candidate.latitude());
            minLongitude = min(minLongitude, candidate.longitude());
            maxLongitude = max(maxLongitude, candidate.longitude());
        }

        List<CatalogCandidate> frozen = List.copyOf(candidates);
        NameVariantIndex variantIndex = NameVariantIndex.of(regionName, frozen);

        if (minLatitude == null) {
            log.warn("좌표를 가진 카탈로그가 없어 이름만으로 찾습니다. lawdCode={}", lawdCode);
            return new RegionCatalog(frozen, null, null, variantIndex);
        }

        return new RegionCatalog(frozen,
                middle(minLatitude, maxLatitude), middle(minLongitude, maxLongitude), variantIndex);
    }

    private static BigDecimal middle(BigDecimal low, BigDecimal high) {
        return low.add(high).divide(BigDecimal.valueOf(2), 7, RoundingMode.HALF_UP);
    }

    private static BigDecimal min(BigDecimal current, BigDecimal next) {
        return current == null || next.compareTo(current) < 0 ? next : current;
    }

    private static BigDecimal max(BigDecimal current, BigDecimal next) {
        return current == null || next.compareTo(current) > 0 ? next : current;
    }
}
