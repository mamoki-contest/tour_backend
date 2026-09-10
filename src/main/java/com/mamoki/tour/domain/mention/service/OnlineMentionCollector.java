package com.mamoki.tour.domain.mention.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.mention.entity.OnlineMentionEntry;
import com.mamoki.tour.domain.mention.entity.OnlineMentionSnapshot;
import com.mamoki.tour.domain.mention.support.SearchQueryRule;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankSnapshot;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankEntryRepository;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankSnapshotRepository;
import com.mamoki.tour.global.enums.MentionStatus;
import com.mamoki.tour.global.enums.SnapshotStatus;
import com.mamoki.tour.infra.naver.NaverApiHubProperties;
import com.mamoki.tour.infra.naver.NaverAuthenticationException;
import com.mamoki.tour.infra.naver.NaverBlogSearchClient;
import com.mamoki.tour.infra.naver.NaverRateLimitException;

/**
 * 카탈로그를 순회하며 온라인 언급량을 월간 수집한다.
 *
 * <p>전체 수집에 성공했을 때만 새 스냅샷을 활성화한다. 일부만 수집된 스냅샷을 활성화하면
 * 빠진 장소가 언급이 적은 곳처럼 보이기 때문이다. 실패하면 직전 정상 스냅샷이 그대로 남는다.
 *
 * <p>인증 실패와 한도 초과는 남은 호출을 계속해도 의미가 없으므로 즉시 중단한다.
 * 개별 호출 실패는 제한된 횟수만 재시도한다.
 */
@Service
public class OnlineMentionCollector {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int MAX_ATTEMPTS = 3;
    private static final int SAVE_CHUNK = 100;

    private static final Logger log = LoggerFactory.getLogger(OnlineMentionCollector.class);

    private final AttractionRepository attractionRepository;
    private final NaverBlogSearchClient searchClient;
    private final SearchQueryRule queryRule;
    private final OnlineMentionSnapshotWriter writer;
    private final NaverApiHubProperties properties;
    private final TmapRankSnapshotRepository tmapSnapshotRepository;
    private final TmapRankEntryRepository tmapEntryRepository;

    public OnlineMentionCollector(AttractionRepository attractionRepository,
                                  NaverBlogSearchClient searchClient,
                                  SearchQueryRule queryRule,
                                  OnlineMentionSnapshotWriter writer,
                                  NaverApiHubProperties properties,
                                  TmapRankSnapshotRepository tmapSnapshotRepository,
                                  TmapRankEntryRepository tmapEntryRepository) {
        this.attractionRepository = attractionRepository;
        this.searchClient = searchClient;
        this.queryRule = queryRule;
        this.writer = writer;
        this.properties = properties;
        this.tmapSnapshotRepository = tmapSnapshotRepository;
        this.tmapEntryRepository = tmapEntryRepository;
    }

    public OnlineMentionCollectResult collect(YearMonth month) {
        List<Attraction> catalog = attractionRepository.findAllWithRegion();

        if (catalog.isEmpty()) {
            throw new IllegalStateException("관광지 카탈로그가 비어 있어 수집할 대상이 없습니다.");
        }

        Map<String, Integer> nameCounts = countNormalizedNames(catalog);
        Set<String> tmapRanked = findTmapRankedContentIds();
        OnlineMentionSnapshot snapshot =
                writer.start(month.format(MONTH), queryRule.version(), catalog.size());

        int collected = 0;
        int ambiguous = 0;
        int unavailable = 0;
        List<OnlineMentionEntry> buffer = new ArrayList<>(SAVE_CHUNK);

        try {
            for (Attraction attraction : catalog) {
                OnlineMentionEntry entry = collectOne(snapshot, attraction, nameCounts, tmapRanked);
                buffer.add(entry);

                switch (entry.getStatus()) {
                    case COLLECTED -> collected++;
                    case AMBIGUOUS -> ambiguous++;
                    default -> unavailable++;
                }

                if (buffer.size() >= SAVE_CHUNK) {
                    writer.saveEntries(buffer);
                    buffer.clear();
                }
            }

            if (!buffer.isEmpty()) {
                writer.saveEntries(buffer);
            }

        } catch (RuntimeException e) {
            writer.fail(snapshot.getId(), e.getMessage(), collected);
            log.warn("온라인 언급량 수집 실패. version={}, 수집={}/{}",
                    snapshot.getVersion(), collected, catalog.size(), e);
            throw e;
        }

        OnlineMentionSnapshot activated = writer.activate(snapshot.getId(), collected);

        log.info("온라인 언급량 수집 완료: version={}, 대상={}, 수집={}, 모호={}, 대상아님={}",
                activated.getVersion(), catalog.size(), collected, ambiguous, unavailable);

        return new OnlineMentionCollectResult(activated, catalog.size(), collected, ambiguous, unavailable);
    }

    private OnlineMentionEntry collectOne(OnlineMentionSnapshot snapshot, Attraction attraction,
                                          Map<String, Integer> nameCounts, Set<String> tmapRanked) {

        String query = queryRule.build(attraction.getName(), regionName(attraction));

        if (query == null) {
            return entry(snapshot, attraction, null, null, MentionStatus.UNAVAILABLE,
                    "검색어를 만들 수 없습니다.");
        }

        // 같은 이름이 카탈로그에 여럿이면 검색 결과가 어느 장소의 것인지 가릴 수 없다.
        String normalized = PlaceNameNormalizer.normalize(attraction.getName());
        int duplicates = normalized == null ? 1 : nameCounts.getOrDefault(normalized, 1);

        if (duplicates > 1) {
            return entry(snapshot, attraction, query, null, MentionStatus.AMBIGUOUS,
                    "카탈로그에 같은 이름이 %d건 있습니다.".formatted(duplicates));
        }

        Long total = searchWithRetry(query);

        // 검색되는 장소인데 0 건이면 표기가 달라 못 찾은 것이다. 그 0 을 언급량으로 저장하지 않는다.
        if (total != null && total == 0L && tmapRanked.contains(attraction.getContentId())) {
            return entry(snapshot, attraction, query, total, MentionStatus.AMBIGUOUS,
                    "TMAP 검색순위에 수록된 장소인데 언급량이 0건입니다. 표기가 다를 수 있습니다.");
        }

        return entry(snapshot, attraction, query, total, MentionStatus.COLLECTED, null);
    }

    /**
     * 활성 TMAP 스냅샷에 확정 매칭된 관광지 식별자.
     *
     * <p>TMAP 순위에 오른 장소는 실제로 검색되는 장소다. 그런 장소의 언급량이 0 건이면
     * 언급이 없는 것이 아니라 우리 검색어가 그 장소를 못 짚은 것으로 본다.
     *
     * <p>입장객 통계도 같은 근거가 되지만 임포터가 아직 없다. 값을 갖게 되면 여기에 더한다.
     *
     * @return 활성 스냅샷이 없으면 빈 집합. 판정을 건너뛰고 기존 동작을 유지한다.
     */
    private Set<String> findTmapRankedContentIds() {
        Optional<TmapRankSnapshot> active =
                tmapSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE);

        if (active.isEmpty()) {
            log.info("활성 TMAP 스냅샷이 없어 0건 검증을 건너뜁니다.");
            return Set.of();
        }

        return Set.copyOf(tmapEntryRepository.findMatchedContentIds(active.get()));
    }

    /**
     * 개별 호출 실패만 재시도한다. 인증 실패와 한도 초과는 재시도해도 같으므로 그대로 던진다.
     */
    private Long searchWithRetry(String query) {
        RuntimeException last = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                sleepBetweenCalls();
                return searchClient.search(query).total();
            } catch (NaverAuthenticationException | NaverRateLimitException e) {
                throw e;
            } catch (RuntimeException e) {
                last = e;
                log.debug("검색 실패 {}/{}: {}", attempt, MAX_ATTEMPTS, query);
            }
        }

        throw last;
    }

    /** 키당 호출 한도를 넘지 않도록 간격을 둔다. */
    private void sleepBetweenCalls() {
        long millis = properties.callDelay() == null ? 0 : properties.callDelay().toMillis();

        if (millis <= 0) {
            return;
        }

        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("수집이 중단되었습니다.", e);
        }
    }

    private Map<String, Integer> countNormalizedNames(List<Attraction> catalog) {
        Map<String, Integer> counts = new HashMap<>();

        for (Attraction attraction : catalog) {
            String normalized = PlaceNameNormalizer.normalize(attraction.getName());

            if (normalized != null) {
                counts.merge(normalized, 1, Integer::sum);
            }
        }

        return counts;
    }

    private static String regionName(Attraction attraction) {
        return attraction.getRegionCode() == null ? null : attraction.getRegionCode().getName();
    }

    private static OnlineMentionEntry entry(OnlineMentionSnapshot snapshot, Attraction attraction,
                                            String query, Long total, MentionStatus status, String note) {
        return OnlineMentionEntry.builder()
                .snapshot(snapshot)
                .contentId(attraction.getContentId())
                .placeName(attraction.getName())
                .searchQuery(query)
                .mentionTotal(total)
                .status(status)
                .collectedAt(status == MentionStatus.COLLECTED ? LocalDateTime.now() : null)
                .note(note)
                .build();
    }
}
