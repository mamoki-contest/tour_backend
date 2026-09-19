package com.mamoki.tour.domain.placemapping.importer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.mamoki.tour.domain.placemapping.support.PlaceMappingDecider;
import com.mamoki.tour.domain.placemapping.support.PlaceMappingDecision;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.kakao.KakaoAuthenticationException;
import com.mamoki.tour.infra.kakao.KakaoLocalClient;
import com.mamoki.tour.infra.kakao.KakaoRateLimitException;
import com.mamoki.tour.infra.kakao.dto.KakaoPlace;

/**
 * 카탈로그에 잇지 못한 원천 이름을 카카오로 찾아 매핑 표에 남긴다.
 *
 * <p><b>이 배치는 조회 응답을 바꾸지 않는다.</b> 판정을 표에 남길 뿐이고, TMAP·입장객은
 * 다음 적재가 그 표를 읽어 이어 붙인다. 스냅샷은 그 적재가 무엇을 만들었는지의 기록이라
 * 나중에 고쳐 넣으면 그 뜻이 무너진다. 연관 장소는 조회 시점에 매핑을 보므로 바로 반영된다.
 *
 * <p>멈춰야 할 때 멈춘다. 인증 실패와 한도 초과는 남은 이름을 계속 불러도 모두 같은 답을
 * 받는 상황이라 그 자리에서 끝낸다. 그때까지 판정한 것은 이름마다 따로 커밋되어 남는다.
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
        this.decider = new PlaceMappingDecider(properties.boundaryMeters());
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

        List<UnmatchedPlaceName> names = collector.collect();

        log.info("{} 원천의 미매칭 이름 {}건을 판정합니다. 거리 경계={}m",
                source.optionValue(), names.size(), decider.boundaryMeters());

        return decideAll(source, names);
    }

    private PlaceMappingResult decideAll(MappingSource source, List<UnmatchedPlaceName> names) {
        Map<String, PlaceMapping> existing = existingByKey(source);
        Map<String, RegionCatalog> catalogs = new HashMap<>();

        int skipped = 0;
        int calls = 0;
        int confirmed = 0;
        int lowConfidence = 0;
        int unmatched = 0;
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

            RegionCatalog catalog = catalogs.computeIfAbsent(name.lawdCode(), this::loadRegion);

            List<KakaoPlace> places;
            try {
                places = search(name, catalog);
                calls++;
            } catch (KakaoAuthenticationException e) {
                stoppedReason = "카카오 인증 실패";
                log.error("카카오 인증에 실패해 장소 매핑을 멈춥니다. 남은 이름은 다음 실행이 봅니다.", e);
                break;
            } catch (KakaoRateLimitException e) {
                calls++;
                stoppedReason = "카카오 호출 한도 초과";
                log.error("카카오 호출 한도를 넘어 장소 매핑을 멈춥니다. 남은 이름은 다음 실행이 봅니다.", e);
                break;
            } catch (ExternalApiException e) {
                // 이 이름 하나의 실패다. 판정을 남기지 않고 다음 이름으로 간다. 값을 지어내지 않는다.
                calls++;
                log.warn("카카오 검색에 실패해 이 이름은 건너뜁니다: {} ({})",
                        name.sourceName(), name.regionName(), e);
                continue;
            }

            PlaceMappingDecision decision =
                    decider.decide(name.sourceName(), name.regionName(), places, catalog.candidates());

            if (!writer.record(source, name, decision, LocalDateTime.now())) {
                skipped++;
                continue;
            }

            switch (decision.status()) {
                case CONFIRMED -> confirmed++;
                case LOW_CONFIDENCE -> lowConfidence++;
                case UNMATCHED -> unmatched++;
            }

            sleepBetweenCalls();
        }

        PlaceMappingResult result = new PlaceMappingResult(source, names.size(), skipped, calls,
                confirmed, lowConfidence, unmatched, stoppedReason);

        log.info("장소 매핑 완료: source={}, {}", source.optionValue(), result.summary());

        if (confirmed > 0 && source != MappingSource.RELATED_PLACE) {
            // 스냅샷은 그 적재가 무엇을 만들었는지의 기록이라 여기서 고쳐 넣지 않는다.
            log.info("확정 매핑 {}건을 스냅샷에 반영하려면 --job={} 을 다시 실행하세요.",
                    confirmed, source.optionValue());
        }

        return result;
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
                                 BigDecimal latitude, BigDecimal longitude) {
    }

    private RegionCatalog loadRegion(String lawdCode) {
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

        if (minLatitude == null) {
            log.warn("좌표를 가진 카탈로그가 없어 이름만으로 찾습니다. lawdCode={}", lawdCode);
            return new RegionCatalog(List.copyOf(candidates), null, null);
        }

        return new RegionCatalog(List.copyOf(candidates),
                middle(minLatitude, maxLatitude), middle(minLongitude, maxLongitude));
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
