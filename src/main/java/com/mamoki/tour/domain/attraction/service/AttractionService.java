package com.mamoki.tour.domain.attraction.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.dto.AttractionListResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSearchRequest;
import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.korservice.KorServiceClient;
import com.mamoki.tour.infra.korservice.KorServiceItemConverter;
import com.mamoki.tour.infra.korservice.dto.KorServiceResponse;

/**
 * 관광지 목록 조회.
 *
 * <p>공급자 호출은 캐시 계층을 거치므로 여기서 예외가 새어 나가지 않는다. 데이터를 얻지
 * 못하면 빈 목록이 아니라 {@code NO_DATA} 상태를 담은 응답을 돌려준다.
 */
@Service
public class AttractionService {

    /** 한국관광공사 영역 코드. 강원. */
    private static final String GANGWON_AREA_CODE = "32";

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String OPERATION = "areaBasedList2";

    private static final Logger log = LoggerFactory.getLogger(AttractionService.class);

    private final KorServiceClient korServiceClient;
    private final ExternalApiCacheService cacheService;
    private final RegionCodeRepository regionCodeRepository;

    public AttractionService(KorServiceClient korServiceClient,
                             ExternalApiCacheService cacheService,
                             RegionCodeRepository regionCodeRepository) {
        this.korServiceClient = korServiceClient;
        this.cacheService = cacheService;
        this.regionCodeRepository = regionCodeRepository;
    }

    public AttractionListResponse search(AttractionSearchRequest request) {
        int page = request.pageOrDefault();
        int size = request.sizeOrDefault();

        String requestKey = korServiceClient.areaBasedListKey(
                GANGWON_AREA_CODE, request.sigunguCode(), request.contentTypeId(), page, size);

        CachedResponse cached = cacheService.fetch(
                ApiProvider.KOR_SERVICE2,
                requestKey,
                () -> korServiceClient.areaBasedListJson(
                        GANGWON_AREA_CODE, request.sigunguCode(), request.contentTypeId(), page, size),
                CACHE_TTL);

        if (!cached.hasBody()) {
            return AttractionListResponse.noData(page, size, KorServiceItemConverter.SOURCE);
        }

        KorServiceResponse parsed;
        try {
            parsed = korServiceClient.parse(OPERATION, cached.body());
        } catch (ExternalApiException e) {
            // 캐시에 남아 있던 본문이 더 이상 해석되지 않는 경우. 빈 목록으로 위장하지 않는다.
            log.warn("캐시된 KorService2 응답을 해석하지 못했습니다. requestKey={}", requestKey, e);
            return AttractionListResponse.noData(page, size, KorServiceItemConverter.SOURCE);
        }

        List<AttractionSnapshot> snapshots = KorServiceItemConverter.convertAll(parsed.items());
        Map<String, RegionCode> regionsByLawdCode = regionCodeRepository
                .findAllByAreaCode(GANGWON_AREA_CODE).stream()
                .collect(java.util.stream.Collectors.toMap(RegionCode::getLawdCode, Function.identity()));

        List<AttractionResponse> items = snapshots.stream()
                .map(snapshot -> AttractionResponse.of(snapshot, regionName(regionsByLawdCode, snapshot)))
                .toList();

        return new AttractionListResponse(
                items,
                parsed.totalCount(),
                page,
                size,
                cached.status(),
                cached.collectedAt(),
                KorServiceItemConverter.SOURCE);
    }

    /** 매핑이 없으면 지역명을 만들어내지 않고 null 로 둔다. */
    private String regionName(Map<String, RegionCode> regionsByLawdCode, AttractionSnapshot snapshot) {
        if (snapshot.lawdCode() == null) {
            return null;
        }

        RegionCode regionCode = regionsByLawdCode.get(snapshot.lawdCode());
        return regionCode == null ? null : regionCode.getName();
    }
}
