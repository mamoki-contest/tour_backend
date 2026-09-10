package com.mamoki.tour.domain.attraction.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.dto.AttractionDetailResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionDetailSnapshot;
import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.relatedplace.dto.RelatedPlaces;
import com.mamoki.tour.domain.relatedplace.service.RelatedPlaceService;
import com.mamoki.tour.domain.visittiming.dto.VisitTimingDetail;
import com.mamoki.tour.domain.visittiming.service.VisitTimingService;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.global.exception.ServiceException;
import com.mamoki.tour.global.rsdata.ResultCodes;
import com.mamoki.tour.infra.korservice.KorServiceClient;
import com.mamoki.tour.infra.korservice.KorServiceItemConverter;
import com.mamoki.tour.infra.korservice.dto.KorServiceResponse;

/**
 * 관광지 상세 조회.
 *
 * <p>기본정보·30일 예측·연관 장소를 각각 독립적으로 모아 하나의 응답으로 조립한다. 셋은 서로
 * 다른 공급자에서 오고 갱신 주기도 달라, 하나가 비어도 나머지를 채운다.
 *
 * <p>다만 <b>기본정보만은 예외</b>다. 기본정보를 얻지 못하면 무엇에 대한 상세인지 말할 수
 * 없으므로 404 로 응답한다. 이때는 예측과 연관 장소도 조회하지 않는다.
 */
@Service
public class AttractionDetailService {

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String OPERATION = "detailCommon2";

    private final KorServiceClient korServiceClient;
    private final ExternalApiCacheService cacheService;
    private final RegionCodeRepository regionCodeRepository;
    private final VisitTimingService visitTimingService;
    private final RelatedPlaceService relatedPlaceService;

    public AttractionDetailService(KorServiceClient korServiceClient,
                                   ExternalApiCacheService cacheService,
                                   RegionCodeRepository regionCodeRepository,
                                   VisitTimingService visitTimingService,
                                   RelatedPlaceService relatedPlaceService) {
        this.korServiceClient = korServiceClient;
        this.cacheService = cacheService;
        this.regionCodeRepository = regionCodeRepository;
        this.visitTimingService = visitTimingService;
        this.relatedPlaceService = relatedPlaceService;
    }

    public AttractionDetailResponse getDetail(String contentId) {
        return getDetail(contentId, LocalDate.now());
    }

    /**
     * @param today 지원 범위(오늘부터 30일)의 기준일
     * @throws ServiceException 기본정보를 얻지 못한 경우 404
     */
    public AttractionDetailResponse getDetail(String contentId, LocalDate today) {
        BasicLookup lookup = findBasic(contentId);

        if (lookup.detail() == null) {
            throw new ServiceException(ResultCodes.NOT_FOUND, "관광지 정보를 찾을 수 없습니다.");
        }

        AttractionDetailSnapshot detail = lookup.detail();
        CachedResponse cached = lookup.cached();
        AttractionSnapshot basic = detail.basic();
        VisitTimingDetail timing = visitTimingService.resolveDetail(basic, today);
        RelatedPlaces related = relatedPlaceService.resolve(basic, today);

        return new AttractionDetailResponse(
                basic.contentId(),
                basic.name(),
                basic.imageUrl(),
                basic.address(),
                detail.zipcode(),
                detail.tel(),
                detail.homepage(),
                detail.overview(),
                basic.latitude(),
                basic.longitude(),
                basic.contentTypeId(),
                basic.lawdCode(),
                regionName(basic.lawdCode()),
                basic.baseAt(),
                cached.status(),
                cached.collectedAt(),
                KorServiceItemConverter.SOURCE,
                timing.summary(),
                timing.daily(),
                related.alternatives(),
                related.companions());
    }

    /**
     * 기본정보 한 건을 조회한다.
     *
     * <p>컬렉션 일괄 재조회(#9)도 같은 경로를 쓴다. 여기서는 없는 항목을 404 로 만들지 않고
     * 결과에 그대로 담아, 호출부가 "없어진 것"과 "지금 확인할 수 없는 것"을 가려낼 수 있게 한다.
     */
    public BasicLookup findBasic(String contentId) {
        CachedResponse cached = fetchDetail(contentId);

        if (!cached.hasBody()) {
            return new BasicLookup(null, cached);
        }

        return new BasicLookup(parse(cached.body()), cached);
    }

    /**
     * @param detail 공급자에 항목이 없거나 해석하지 못하면 null
     * @param cached 캐시 계층의 응답 상태. NO_DATA 면 공급자를 부르지 못한 것이다.
     */
    public record BasicLookup(AttractionDetailSnapshot detail, CachedResponse cached) {

        /** 공급자에게 물어보지도 못한 상태. 항목이 없어졌다는 뜻이 아니다. */
        public boolean isUnreachable() {
            return !cached.hasBody();
        }
    }

    private CachedResponse fetchDetail(String contentId) {
        return cacheService.fetch(
                ApiProvider.KOR_SERVICE2,
                korServiceClient.detailCommonKey(contentId),
                () -> korServiceClient.detailCommonJson(contentId),
                CACHE_TTL);
    }

    /**
     * @return 항목이 없거나 표준 식별자·이름이 비어 상세를 구성할 수 없으면 null
     */
    private AttractionDetailSnapshot parse(String body) {
        KorServiceResponse response;
        try {
            response = korServiceClient.parse(OPERATION, body);
        } catch (ExternalApiException e) {
            // 캐시에 남아 있던 본문이 더 이상 해석되지 않는 경우. 빈 상세로 위장하지 않는다.
            return null;
        }

        return response.items().stream()
                .map(KorServiceItemConverter::convertDetail)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** 매핑이 없으면 지역명을 만들어내지 않고 null 로 둔다. */
    private String regionName(String lawdCode) {
        if (lawdCode == null) {
            return null;
        }

        return regionCodeRepository.findByLawdCode(lawdCode)
                .map(RegionCode::getName)
                .orElse(null);
    }
}
