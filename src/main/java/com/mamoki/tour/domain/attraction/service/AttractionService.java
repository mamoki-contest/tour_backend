package com.mamoki.tour.domain.attraction.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
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
import com.mamoki.tour.domain.attraction.support.AttractionSortOrder;
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
 */
@Service
public class AttractionService {

    /** 한국관광공사 영역 코드. 강원. */
    private static final String GANGWON_AREA_CODE = "32";

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String OPERATION = "areaBasedList2";

    /** 정렬을 위해 범위 전체를 모을 때 한 번에 받아오는 크기. */
    private static final int SORT_FETCH_SIZE = 100;

    /**
     * 정렬 대상 상한. 강원 전체 관광지가 3천 건 이하라 이 값이면 범위 전체를 담는다.
     * 넘어서면 공급자 순서 기준으로 잘리므로 정렬 결과가 범위 전체를 반영하지 못한다.
     */
    private static final int SORT_MAX_ITEMS = 3_000;

    private static final Logger log = LoggerFactory.getLogger(AttractionService.class);

    private final KorServiceClient korServiceClient;
    private final ExternalApiCacheService cacheService;
    private final RegionCodeRepository regionCodeRepository;
    private final CenterRankService centerRankService;
    private final SignalLookupService signalLookupService;
    private final VisitTimingService visitTimingService;
    private final AttractionSortOrder sortOrder = new AttractionSortOrder();

    public AttractionService(KorServiceClient korServiceClient,
                             ExternalApiCacheService cacheService,
                             RegionCodeRepository regionCodeRepository,
                             CenterRankService centerRankService,
                             SignalLookupService signalLookupService,
                             VisitTimingService visitTimingService) {
        this.korServiceClient = korServiceClient;
        this.cacheService = cacheService;
        this.regionCodeRepository = regionCodeRepository;
        this.centerRankService = centerRankService;
        this.signalLookupService = signalLookupService;
        this.visitTimingService = visitTimingService;
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

        Fetched fetched = fetchPage(request, page, size);

        if (fetched.isEmpty()) {
            return AttractionListResponse.noData(page, size, KorServiceItemConverter.SOURCE, null);
        }

        List<AttractionResponse> items = toResponses(fetched.snapshots(), request);

        return new AttractionListResponse(items, fetched.totalCount(), page, size, null,
                fetched.status(), fetched.collectedAt(), KorServiceItemConverter.SOURCE);
    }

    /**
     * 조회 범위 전체를 모아 경계로 거르고 정렬한 뒤 페이지를 나눈다.
     *
     * <p>활성 언급량 스냅샷이 없으면 정렬 기준 자체가 없다. 그때는 순서를 만들어내지 않고
     * 공급자 순서를 그대로 쓰며, 각 항목의 상태로 그 사실을 알린다.
     */
    private AttractionListResponse searchWholeRange(AttractionSearchRequest request) {
        int page = request.pageOrDefault();
        int size = request.sizeOrDefault();

        Fetched fetched = fetchWholeRange(request);

        if (fetched.isEmpty()) {
            return AttractionListResponse.noData(page, size, KorServiceItemConverter.SOURCE, request.sort());
        }

        // 신호 조회와 정렬 전에 거른다. 경계 밖 장소의 언급량까지 찾을 이유가 없다.
        List<AttractionSnapshot> withinBounds = filterByBounds(fetched.snapshots(), request.bounds());

        List<AttractionResponse> all = toResponses(withinBounds, request);

        // 경계만 준 요청은 정렬 기준이 없다. 공급자 순서를 그대로 두고 거르기만 한다.
        List<AttractionResponse> ordered = request.sort() == null
                ? all
                : sortOrder.order(all, request.sort());

        List<AttractionResponse> paged = pageOf(ordered, page, size);

        return new AttractionListResponse(paged, ordered.size(), page, size, request.sort(),
                fetched.status(), fetched.collectedAt(), KorServiceItemConverter.SOURCE);
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
                        visitTimings.get(snapshot.contentId())))
                .toList();
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

    private Fetched fetchPage(AttractionSearchRequest request, int page, int size) {
        String requestKey = korServiceClient.areaBasedListKey(
                GANGWON_AREA_CODE, request.sigunguCode(), request.contentTypeId(), page, size);

        CachedResponse cached = cacheService.fetch(
                ApiProvider.KOR_SERVICE2,
                requestKey,
                () -> korServiceClient.areaBasedListJson(
                        GANGWON_AREA_CODE, request.sigunguCode(), request.contentTypeId(), page, size),
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

    /** 정렬 대상을 모으기 위해 조회 범위의 모든 페이지를 받아온다. */
    private Fetched fetchWholeRange(AttractionSearchRequest request) {
        List<AttractionSnapshot> all = new ArrayList<>();
        Fetched first = fetchPage(request, 1, SORT_FETCH_SIZE);

        if (first.isEmpty()) {
            return first;
        }

        all.addAll(first.snapshots());

        int totalCount = Math.min(first.totalCount(), SORT_MAX_ITEMS);
        DataStatus status = first.status();

        for (int page = 2; all.size() < totalCount; page++) {
            Fetched next = fetchPage(request, page, SORT_FETCH_SIZE);

            if (next.isEmpty() || next.snapshots().isEmpty()) {
                break;
            }

            all.addAll(next.snapshots());

            // 한 페이지라도 최종 정상 데이터로 응답했다면 전체를 그 상태로 알린다.
            if (next.status() == DataStatus.STALE) {
                status = DataStatus.STALE;
            }
        }

        if (first.totalCount() > SORT_MAX_ITEMS) {
            log.warn("정렬 대상이 상한을 넘었습니다. totalCount={}, 상한={}",
                    first.totalCount(), SORT_MAX_ITEMS);
        }

        return new Fetched(all, all.size(), status, first.collectedAt());
    }

    /** 공급자에서 받아온 한 묶음과 그 데이터 상태. */
    private record Fetched(List<AttractionSnapshot> snapshots, int totalCount,
                           DataStatus status, java.time.LocalDateTime collectedAt) {

        static Fetched empty() {
            return new Fetched(List.of(), 0, DataStatus.NO_DATA, null);
        }

        boolean isEmpty() {
            return status == DataStatus.NO_DATA && snapshots.isEmpty();
        }
    }
}
