package com.mamoki.tour.domain.attraction.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.attraction.dto.CenterRank;
import com.mamoki.tour.domain.attraction.support.Coordinates;
import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.locgohub.LocgoHubClient;
import com.mamoki.tour.infra.locgohub.LocgoHubItemConverter;

/**
 * 시·군 내부 중심관광지 순위를 표준 관광지에 이어 붙인다.
 *
 * <p>LocgoHub 는 데이터랩 식별자를 쓰고 KorService2 는 contentId 를 쓴다. 공통 키가 없어
 * 정규화한 이름으로 후보를 찾고, 양쪽에 좌표가 있으면 거리로 확인한다. 이름이 같아도
 * 좌표가 멀면 다른 장소로 보고 순위를 붙이지 않는다.
 *
 * <p>매칭에 실패하면 순위를 만들어내지 않고 비워 둔다. 순위 없음이 곧 낮은 순위는 아니다.
 */
@Service
public class CenterRankService {

    /** 같은 이름이라도 이보다 멀면 다른 장소로 본다. */
    private static final double MAX_MATCH_DISTANCE_METERS = 3_000;

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final int MAX_ROWS = 100;

    private static final Logger log = LoggerFactory.getLogger(CenterRankService.class);

    private final LocgoHubClient locgoHubClient;
    private final ExternalApiCacheService cacheService;

    public CenterRankService(LocgoHubClient locgoHubClient, ExternalApiCacheService cacheService) {
        this.locgoHubClient = locgoHubClient;
        this.cacheService = cacheService;
    }

    /**
     * @param lawdCode    법정동 시·군 코드 5자리
     * @param attractions 순위를 붙일 대상
     * @return 표준 관광지 식별자 → 시·군 내부 순위. 매칭되지 않은 관광지는 담기지 않는다.
     */
    public Map<String, Integer> resolveRanks(String lawdCode, List<AttractionSnapshot> attractions) {
        List<CenterRank> centerRanks = fetchCenterRanks(lawdCode);

        if (centerRanks.isEmpty() || attractions.isEmpty()) {
            return Map.of();
        }

        Map<String, CenterRank> byNormalizedName = new HashMap<>();
        for (CenterRank centerRank : centerRanks) {
            if (centerRank.normalizedName() != null) {
                byNormalizedName.putIfAbsent(centerRank.normalizedName(), centerRank);
            }
        }

        Map<String, Integer> ranks = new HashMap<>();
        for (AttractionSnapshot attraction : attractions) {
            String normalized = PlaceNameNormalizer.normalize(attraction.name());

            if (normalized == null) {
                continue;
            }

            CenterRank candidate = byNormalizedName.get(normalized);

            if (candidate != null && isSamePlace(attraction, candidate)) {
                ranks.put(attraction.contentId(), candidate.rank());
            }
        }

        return ranks;
    }

    /** 좌표가 없으면 이름 일치만으로 인정한다. 있으면 거리로 확인한다. */
    private boolean isSamePlace(AttractionSnapshot attraction, CenterRank candidate) {
        Double distance = Coordinates.distanceMeters(
                attraction.latitude(), attraction.longitude(),
                candidate.latitude(), candidate.longitude());

        return distance == null || distance <= MAX_MATCH_DISTANCE_METERS;
    }

    private List<CenterRank> fetchCenterRanks(String lawdCode) {
        String requestKey = locgoHubClient.areaBasedListKey(lawdCode, MAX_ROWS);

        CachedResponse cached = cacheService.fetch(
                ApiProvider.LOCGO_HUB_TAR,
                requestKey,
                () -> locgoHubClient.areaBasedListJson(lawdCode, MAX_ROWS),
                CACHE_TTL);

        if (!cached.hasBody()) {
            return List.of();
        }

        try {
            return LocgoHubItemConverter.convertAll(locgoHubClient.parse(cached.body()).items());
        } catch (ExternalApiException e) {
            log.warn("캐시된 LocgoHub 응답을 해석하지 못했습니다. lawdCode={}", lawdCode, e);
            return List.of();
        }
    }
}
