package com.mamoki.tour.domain.attraction.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.dto.AttractionListResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSearchRequest;
import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.attraction.dto.AttractionSort;
import com.mamoki.tour.domain.attraction.dto.OnlineMentionView;
import com.mamoki.tour.domain.attraction.dto.TmapRankView;
import com.mamoki.tour.domain.attraction.dto.VisitorStatsView;
import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.attraction.support.AttractionSortOrder;
import com.mamoki.tour.domain.attraction.support.AttractionSortOrder.Ordered;
import com.mamoki.tour.domain.attraction.support.MapBounds;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;
import com.mamoki.tour.domain.visittiming.enums.DateMode;
import com.mamoki.tour.domain.visittiming.service.VisitTimingService;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.global.exception.ServiceException;
import com.mamoki.tour.global.rsdata.ResultCodes;
import com.mamoki.tour.infra.korservice.KorServiceClient;
import com.mamoki.tour.infra.korservice.KorServiceItemConverter;
import com.mamoki.tour.infra.korservice.dto.KorServiceResponse;

/**
 * 관광지 목록 조회.
 *
 * <p>공급자 호출은 캐시 계층을 거치므로 여기서 예외가 새어 나가지 않는다. 데이터를 얻지
 * 못하면 빈 목록이 아니라 {@code NO_DATA} 상태를 담은 응답을 돌려준다.
 *
 * <p>정렬을 요청하면 현재 조회 범위 전체를 모아 정렬한 뒤 페이지를 나눈다. 한 페이지 안에서만
 * 정렬하면 공급자가 준 임의의 20건을 정렬하는 셈이라 순서에 의미가 없다.
 *
 * <p><b>범위 전체가 필요한 조회(정렬·지도 경계)는 DB 카탈로그를 대상으로 한다(#51).</b>
 * 공급자 페이지를 끝까지 모으던 방식은 상한에서 잘려, 강원 전체를 정렬하면 카탈로그
 * 4,700곳대 중 앞쪽 3,000곳만 줄 세우고 나머지는 경고 로그만 남기고 사라졌다. 카탈로그는
 * 언급량 수집이 순회하는 바로 그 집합이라, 카탈로그를 대상으로 삼으면 언급량을 산정한
 * 집합과 정렬하는 집합이 같아진다. 공급자 호출도 필요 없다.
 */
@Service
public class AttractionService {

    /** 한국관광공사 영역 코드. 강원. 지역 매핑을 찾을 때만 쓴다. */
    private static final String GANGWON_AREA_CODE = "32";

    /**
     * 법정동 시·도 코드. 강원.
     *
     * <p>공급자 조회는 영역 코드가 아니라 법정동 코드로 한다. 영역 코드로 거르면 공급자
     * 데이터에서 areacode 가 빈 항목이 통째로 빠져 남이섬·레고랜드가 목록에 나오지 않는다(#46).
     */
    private static final String GANGWON_LAWD_REGION_CODE = "51";

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String OPERATION = "areaBasedList2";

    /** 범위 전체를 공급자에서 모을 때 한 번에 받아오는 크기. */
    private static final int SORT_FETCH_SIZE = 100;

    /**
     * 카탈로그가 비어 공급자로 폴백했을 때의 상한.
     *
     * <p>평소에는 쓰이지 않는다. 카탈로그를 아직 적재하지 않은 환경에서만 이 경로로 오며,
     * 실측 카탈로그(4,700곳대)가 두 배로 늘어도 잘리지 않도록 잡았다. 공급자가 갑자기 훨씬
     * 많은 값을 돌려줄 때 호출이 끝없이 늘어나지 않도록 두는 상한이다.
     */
    private static final int PROVIDER_FALLBACK_MAX_ITEMS = 10_000;

    /**
     * 카탈로그에서 읽은 목록의 기본 순서.
     *
     * <p>정렬을 요청하지 않은 경계 조회도 페이지를 나눠야 해서 순서가 고정돼 있어야 한다.
     * DB 가 돌려주는 순서에 맡기면 페이지를 넘길 때 같은 항목이 두 번 나오거나 빠진다.
     */
    private static final Comparator<Attraction> CATALOG_ORDER =
            Comparator.comparing(Attraction::getName, Comparator.nullsLast(String::compareTo))
                    .thenComparing(Attraction::getContentId,
                            Comparator.nullsLast(String::compareTo));

    private static final Logger log = LoggerFactory.getLogger(AttractionService.class);

    private final KorServiceClient korServiceClient;
    private final ExternalApiCacheService cacheService;
    private final RegionCodeRepository regionCodeRepository;
    private final CenterRankService centerRankService;
    private final SignalLookupService signalLookupService;
    private final VisitTimingService visitTimingService;
    private final AttractionRepository attractionRepository;
    private final AttractionSortOrder sortOrder = new AttractionSortOrder();

    public AttractionService(KorServiceClient korServiceClient,
                             ExternalApiCacheService cacheService,
                             RegionCodeRepository regionCodeRepository,
                             CenterRankService centerRankService,
                             SignalLookupService signalLookupService,
                             VisitTimingService visitTimingService,
                             AttractionRepository attractionRepository) {
        this.korServiceClient = korServiceClient;
        this.cacheService = cacheService;
        this.regionCodeRepository = regionCodeRepository;
        this.centerRankService = centerRankService;
        this.signalLookupService = signalLookupService;
        this.visitTimingService = visitTimingService;
        this.attractionRepository = attractionRepository;
    }

    /**
     * 정렬도 경계도 없으면 공급자 페이지를 그대로 쓰고, 둘 중 하나라도 있으면 범위 전체를 모은다.
     *
     * <p>경계 필터는 공급자가 제공하지 않아 우리가 걸러야 한다. 공급자가 나눠 준 한 페이지만
     * 걸러내면 경계 안에 있는데도 뒤 페이지에 있다는 이유로 빠지는 장소가 생긴다.
     */
    public AttractionListResponse search(AttractionSearchRequest request) {
        return request.sort() == null && !request.hasBounds()
                ? searchByProviderOrder(request)
                : searchWholeRange(request);
    }

    /** 정렬을 요청하지 않으면 공급자 페이지를 그대로 쓴다. */
    private AttractionListResponse searchByProviderOrder(AttractionSearchRequest request) {
        int page = request.pageOrDefault();
        int size = request.sizeOrDefault();

        Fetched fetched = fetchPage(request, toLawdSigunguCode(request.sigunguCode()), page, size);

        if (fetched.isEmpty()) {
            return AttractionListResponse.noData(page, size, KorServiceItemConverter.SOURCE, null);
        }

        List<AttractionResponse> items = toResponses(fetched.snapshots(), request);

        return new AttractionListResponse(items, fetched.totalCount(), page, size, null, false,
                fetched.status(), fetched.collectedAt(), KorServiceItemConverter.SOURCE);
    }

    /**
     * 범위 전체가 필요한 조회. 카탈로그를 대상으로 하고, 카탈로그가 비어 있을 때만 공급자로 간다.
     *
     * <p>시·군구 코드 검증은 어느 경로로 가든 먼저 한다. 매핑이 없는 코드를 거르지 못한 채
     * 강원 전체를 돌려주면 사용자가 지정한 조건이 조용히 무시된다.
     */
    private AttractionListResponse searchWholeRange(AttractionSearchRequest request) {
        RegionCode region = resolveRegion(request.sigunguCode());
        LocalDateTime catalogChangedAt = attractionRepository.findLatestCatalogChangeAt();

        if (catalogChangedAt == null) {
            // 카탈로그를 아직 적재하지 않은 환경. 공급자에게 물으면 답이 있으므로 NO_DATA 로
            // 위장하지 않는다. 다만 이 경로는 상한에서 잘릴 수 있으니 흔적을 남긴다.
            log.warn("관광지 카탈로그가 비어 있어 공급자 응답으로 정렬합니다. "
                    + "--job=catalog 로 카탈로그를 먼저 적재하세요.");
            return searchWholeRangeFromProvider(request, region);
        }

        return searchWholeRangeFromCatalog(request, region, catalogChangedAt);
    }

    /**
     * 카탈로그를 조회 조건으로 좁혀 경계로 거르고 정렬한 뒤 페이지를 나눈다.
     *
     * <p>조건에 맞는 장소가 없는 것은 정보 없음이 아니다. {@code AVAILABLE} + 빈 목록으로
     * 돌려줘, 프론트가 `조건에 맞는 곳이 없음`과 `데이터를 얻지 못함`을 구분할 수 있게 한다.
     */
    private AttractionListResponse searchWholeRangeFromCatalog(AttractionSearchRequest request,
                                                               RegionCode region,
                                                               LocalDateTime catalogChangedAt) {
        int page = request.pageOrDefault();
        int size = request.sizeOrDefault();

        List<AttractionSnapshot> catalog = readCatalog(region, request.contentTypeId());
        List<AttractionSnapshot> withinBounds = filterByBounds(catalog, request.bounds());

        Ordered ordered = orderBySort(toResponses(withinBounds, request), request.sort());

        return new AttractionListResponse(pageOf(ordered.items(), page, size), ordered.items().size(),
                page, size, request.sort(), ordered.applied(), DataStatus.AVAILABLE, catalogChangedAt,
                KorServiceItemConverter.SOURCE);
    }

    /**
     * 카탈로그가 비어 있을 때만 쓰는 공급자 경로.
     *
     * <p>활성 언급량 스냅샷이 없으면 정렬 기준 자체가 없다. 그때는 순서를 만들어내지 않고
     * 공급자 순서를 그대로 쓰며, 각 항목의 상태와 응답의 {@code sortApplied} 로 알린다(#65).
     */
    private AttractionListResponse searchWholeRangeFromProvider(AttractionSearchRequest request,
                                                                RegionCode region) {
        int page = request.pageOrDefault();
        int size = request.sizeOrDefault();

        Fetched fetched = fetchWholeRange(request, toLawdSigunguCode(region));

        if (fetched.isEmpty()) {
            return AttractionListResponse.noData(page, size, KorServiceItemConverter.SOURCE, request.sort());
        }

        // 신호 조회와 정렬 전에 거른다. 경계 밖 장소의 언급량까지 찾을 이유가 없다.
        List<AttractionSnapshot> withinBounds = filterByBounds(fetched.snapshots(), request.bounds());

        Ordered ordered = orderBySort(toResponses(withinBounds, request), request.sort());

        return new AttractionListResponse(pageOf(ordered.items(), page, size), ordered.items().size(),
                page, size, request.sort(), ordered.applied(), fetched.status(), fetched.collectedAt(),
                KorServiceItemConverter.SOURCE);
    }

    /**
     * 경계만 준 요청은 정렬 기준이 없다. 순서를 만들어내지 않고 들어온 순서를 그대로 둔다.
     *
     * <p>정렬을 요청해도 산정된 장소가 하나도 없으면 같은 결론이다. 그 판단은
     * {@link AttractionSortOrder} 가 하고, 여기서는 그 사실을 응답까지 들고 간다(#65).
     */
    private Ordered orderBySort(List<AttractionResponse> items, AttractionSort sort) {
        return sort == null ? Ordered.notApplied(items) : sortOrder.order(items, sort);
    }

    /**
     * 조회 조건에 맞는 카탈로그를 읽어 표준 계약으로 옮긴다.
     *
     * <p>분류 필터는 메모리에서 건다. 카탈로그가 강원 전체라도 5천 건 규모라 한 번에 읽어도
     * 되고, 조건별 쿼리를 늘리는 것보다 필터가 한자리에 모이는 편이 낫다.
     */
    private List<AttractionSnapshot> readCatalog(RegionCode region, String contentTypeId) {
        List<Attraction> rows = region == null
                ? attractionRepository.findAllWithRegion()
                : attractionRepository.findAllWithRegionByLawdCode(region.getLawdCode());

        return rows.stream()
                .filter(row -> contentTypeId == null || contentTypeId.equals(row.getContentTypeId()))
                .sorted(CATALOG_ORDER)
                .map(AttractionService::toSnapshot)
                .toList();
    }

    /** 카탈로그 행을 목록·검색이 공유하는 표준 계약으로 옮긴다. 값을 만들어 채우지 않는다. */
    private static AttractionSnapshot toSnapshot(Attraction row) {
        return new AttractionSnapshot(
                row.getContentId(),
                row.getName(),
                row.getImageUrl(),
                row.getAddress(),
                row.getLatitude(),
                row.getLongitude(),
                row.getContentTypeId(),
                row.getRegionCode() == null ? null : row.getRegionCode().getLawdCode(),
                row.getBaseAt(),
                row.getSource());
    }

    /** 좌표가 없는 장소는 경계 안이라고 단정할 수 없어 뺀다. 경계를 주지 않았으면 그대로 둔다. */
    private static List<AttractionSnapshot> filterByBounds(List<AttractionSnapshot> snapshots,
                                                          MapBounds bounds) {
        if (bounds == null) {
            return snapshots;
        }

        return snapshots.stream()
                .filter(snapshot -> bounds.contains(snapshot.latitude(), snapshot.longitude()))
                .toList();
    }

    private static List<AttractionResponse> pageOf(List<AttractionResponse> items, int page, int size) {
        int from = Math.min((page - 1) * size, items.size());
        int to = Math.min(from + size, items.size());

        return items.subList(from, to);
    }

    private List<AttractionResponse> toResponses(List<AttractionSnapshot> snapshots,
                                                 AttractionSearchRequest request) {

        return describe(snapshots, request.sigunguCode(), request.dateMode(), request.visitDate());
    }

    /**
     * 공급자 응답을 목록 항목으로 조립한다.
     *
     * <p>검색(#5)도 같은 항목을 내려줘야 해서 밖으로 열어 둔다. 조립을 따로 만들면 지역명이나
     * 언급량 같은 필드가 목록과 검색에서 서로 다르게 채워진다.
     *
     * @param sigunguCode 시·군을 지정한 조회일 때만 중심관광지 순위를 붙인다.
     * @param dateMode    날짜 탐색을 하지 않으면 null.
     */
    public List<AttractionResponse> describe(List<AttractionSnapshot> snapshots,
                                             String sigunguCode,
                                             DateMode dateMode,
                                             LocalDate visitDate) {
        Map<String, RegionCode> regionsByLawdCode = regionCodeRepository
                .findAllByAreaCode(GANGWON_AREA_CODE).stream()
                .collect(Collectors.toMap(RegionCode::getLawdCode, Function.identity()));

        Map<String, Integer> centerRanks = resolveCenterRanks(sigunguCode, snapshots);

        List<String> contentIds = snapshots.stream().map(AttractionSnapshot::contentId).toList();
        Optional<Map<String, OnlineMentionView>> mentions =
                signalLookupService.findOnlineMentions(contentIds);
        Map<String, TmapRankView> tmapRanks = signalLookupService.findTmapRanks(contentIds);
        Optional<Map<String, VisitorStatsView>> visitorStats =
                signalLookupService.findVisitorStats(contentIds);

        String ruleVersion = signalLookupService.findMentionRuleVersion().orElse(null);

        // 날짜 탐색은 선택 기능이다. 모드를 지정하지 않으면 예측을 조회하지 않는다.
        Map<String, VisitTiming> visitTimings =
                visitTimingService.resolve(snapshots, dateMode, visitDate);

        return snapshots.stream()
                .map(snapshot -> AttractionResponse.of(
                        snapshot,
                        regionName(regionsByLawdCode, snapshot),
                        centerRanks.get(snapshot.contentId()),
                        mentionView(mentions, snapshot.contentId(), ruleVersion),
                        tmapRanks.getOrDefault(snapshot.contentId(), TmapRankView.notAvailable()),
                        visitorStatsView(visitorStats, snapshot.contentId()),
                        visitTimings.get(snapshot.contentId())))
                .toList();
    }

    /**
     * 스냅샷이 없으면 아직 적재하지 않은 것이고, 스냅샷은 있는데 그 장소가 없으면 통계에
     * 없거나 공표월에 집계되지 않은 것이다. 둘 다 0 명이 아니다.
     */
    private static VisitorStatsView visitorStatsView(Optional<Map<String, VisitorStatsView>> stats,
                                                     String contentId) {
        if (stats.isEmpty()) {
            return VisitorStatsView.notImported();
        }

        return stats.get().getOrDefault(contentId,
                new VisitorStatsView(VisitorStatsView.Status.NOT_REGISTERED, null, null, null));
    }

    /** 스냅샷 자체가 없으면 값이 아니라 아직 수집하지 않았다는 사실을 전달한다. */
    private static OnlineMentionView mentionView(Optional<Map<String, OnlineMentionView>> mentions,
                                                 String contentId, String ruleVersion) {
        if (mentions.isEmpty()) {
            return OnlineMentionView.notCollected(null);
        }

        return mentions.get().getOrDefault(contentId, OnlineMentionView.notCollected(ruleVersion));
    }

    /**
     * 공개 파라미터인 관광공사 시·군구 코드로 지역 매핑을 찾는다.
     *
     * <p>프론트가 보내는 값의 의미는 그대로 두고 안에서만 바꾼다. 파라미터를 법정동 코드로
     * 바꾸면 이미 쓰고 있는 쪽이 전부 깨진다.
     *
     * @return 시·군을 지정하지 않았으면 null.
     * @throws ServiceException 매핑이 없는 시·군구 코드인 경우. 거르지 못한 채 강원 전체를
     *                          돌려주면 사용자가 지정한 조건이 조용히 무시된다.
     */
    private RegionCode resolveRegion(String sigunguCode) {
        if (sigunguCode == null) {
            return null;
        }

        return regionCodeRepository.findByAreaCodeAndSigunguCode(GANGWON_AREA_CODE, sigunguCode)
                .orElseThrow(() -> new ServiceException(ResultCodes.INVALID_REQUEST,
                        "알 수 없는 시·군구 코드입니다: " + sigunguCode));
    }

    /** @return 공급자에게 보낼 법정동 시·군구 코드 3자리. 시·군을 지정하지 않았으면 null. */
    private String toLawdSigunguCode(String sigunguCode) {
        return toLawdSigunguCode(resolveRegion(sigunguCode));
    }

    private static String toLawdSigunguCode(RegionCode region) {
        return region == null ? null : region.getLawdCode().substring(2);
    }

    /**
     * 시·군을 지정하지 않으면 순위를 붙이지 않는다. 목록이 여러 시·군에 걸칠 때
     * 각 시·군의 내부 순위를 한 줄에 섞으면 시·군 사이의 순위처럼 읽히기 때문이다.
     */
    private Map<String, Integer> resolveCenterRanks(String sigunguCode, List<AttractionSnapshot> snapshots) {
        if (sigunguCode == null || snapshots.isEmpty()) {
            return Map.of();
        }

        return regionCodeRepository.findByAreaCodeAndSigunguCode(GANGWON_AREA_CODE, sigunguCode)
                .map(region -> centerRankService.resolveRanks(region.getLawdCode(), snapshots))
                .orElseGet(Map::of);
    }

    /** 매핑이 없으면 지역명을 만들어내지 않고 null 로 둔다. */
    private String regionName(Map<String, RegionCode> regionsByLawdCode, AttractionSnapshot snapshot) {
        if (snapshot.lawdCode() == null) {
            return null;
        }

        RegionCode regionCode = regionsByLawdCode.get(snapshot.lawdCode());
        return regionCode == null ? null : regionCode.getName();
    }

    private Fetched fetchPage(AttractionSearchRequest request, String lawdSigunguCode,
                              int page, int size) {

        String requestKey = korServiceClient.areaBasedListByLawdKey(
                GANGWON_LAWD_REGION_CODE, lawdSigunguCode, request.contentTypeId(), page, size);

        CachedResponse cached = cacheService.fetch(
                ApiProvider.KOR_SERVICE2,
                requestKey,
                () -> korServiceClient.areaBasedListByLawdJson(
                        GANGWON_LAWD_REGION_CODE, lawdSigunguCode, request.contentTypeId(), page, size),
                CACHE_TTL);

        if (!cached.hasBody()) {
            return Fetched.empty();
        }

        KorServiceResponse parsed;
        try {
            parsed = korServiceClient.parse(OPERATION, cached.body());
        } catch (ExternalApiException e) {
            // 캐시에 남아 있던 본문이 더 이상 해석되지 않는 경우. 빈 목록으로 위장하지 않는다.
            log.warn("캐시된 KorService2 응답을 해석하지 못했습니다. requestKey={}", requestKey, e);
            return Fetched.empty();
        }

        return new Fetched(KorServiceItemConverter.convertAll(parsed.items()),
                parsed.totalCount(), cached.status(), cached.collectedAt());
    }

    /** 카탈로그가 비어 있을 때만 쓴다. 조회 범위의 모든 페이지를 공급자에서 받아온다. */
    private Fetched fetchWholeRange(AttractionSearchRequest request, String lawdSigunguCode) {
        List<AttractionSnapshot> all = new ArrayList<>();
        Fetched first = fetchPage(request, lawdSigunguCode, 1, SORT_FETCH_SIZE);

        if (first.isEmpty()) {
            return first;
        }

        all.addAll(first.snapshots());

        int totalCount = Math.min(first.totalCount(), PROVIDER_FALLBACK_MAX_ITEMS);
        DataStatus status = first.status();

        for (int page = 2; all.size() < totalCount; page++) {
            Fetched next = fetchPage(request, lawdSigunguCode, page, SORT_FETCH_SIZE);

            if (next.isEmpty() || next.snapshots().isEmpty()) {
                break;
            }

            all.addAll(next.snapshots());

            // 한 페이지라도 최종 정상 데이터로 응답했다면 전체를 그 상태로 알린다.
            if (next.status() == DataStatus.STALE) {
                status = DataStatus.STALE;
            }
        }

        if (first.totalCount() > PROVIDER_FALLBACK_MAX_ITEMS) {
            log.warn("공급자 폴백 정렬 대상이 상한을 넘었습니다. totalCount={}, 상한={}",
                    first.totalCount(), PROVIDER_FALLBACK_MAX_ITEMS);
        }

        return new Fetched(all, all.size(), status, first.collectedAt());
    }

    /** 공급자에서 받아온 한 묶음과 그 데이터 상태. */
    private record Fetched(List<AttractionSnapshot> snapshots, int totalCount,
                           DataStatus status, LocalDateTime collectedAt) {

        static Fetched empty() {
            return new Fetched(List.of(), 0, DataStatus.NO_DATA, null);
        }

        boolean isEmpty() {
            return status == DataStatus.NO_DATA && snapshots.isEmpty();
        }
    }
}
