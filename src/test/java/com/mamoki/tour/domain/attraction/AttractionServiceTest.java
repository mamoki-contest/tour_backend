package com.mamoki.tour.domain.attraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.attraction.dto.AttractionListResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSearchRequest;
import com.mamoki.tour.domain.attraction.dto.AttractionSort;
import com.mamoki.tour.domain.attraction.dto.OnlineMentionView;
import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionCatalogImportRepository;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.attraction.service.AttractionService;
import com.mamoki.tour.domain.attraction.service.CenterRankService;
import com.mamoki.tour.domain.attraction.service.SignalLookupService;
import com.mamoki.tour.domain.attraction.service.SignalLookupService.ActiveSignalVersions;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.placeimage.service.PlaceImageLookupService;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;
import com.mamoki.tour.domain.visittiming.enums.DateMode;
import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;
import com.mamoki.tour.domain.visittiming.service.VisitTimingService;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.enums.MentionStatus;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.global.exception.ServiceException;
import com.mamoki.tour.infra.korservice.KorServiceClient;
import com.mamoki.tour.infra.korservice.dto.KorServiceItem;
import com.mamoki.tour.infra.korservice.dto.KorServiceResponse;

/**
 * 관광지 목록 조회.
 *
 * <p>정렬과 경계 조회는 DB 카탈로그를 대상으로 한다. 공급자 응답을 페이지째 모으던 방식은
 * 상한에서 잘려 정렬 대상이 카탈로그 전수보다 작아졌다(#51). 카탈로그는 언급량 수집이
 * 순회하는 바로 그 집합이라, 카탈로그를 대상으로 삼으면 산정 집합과 정렬 집합이 같아진다.
 *
 * <p>컨트롤러 테스트는 이 서비스를 목으로 두므로 실제 정렬 경로가 덮이지 않는다. 여기서 덮는다.
 */
class AttractionServiceTest {

    /**
     * 강원 카탈로그 규모. 2026-09-19 에 `areaBasedList2 lDongRegnCd=51` 로 실측한
     * {@code totalCount} 다. 예전 정렬 상한 3,000 을 넘는다는 것이 이 상수의 요점이다.
     *
     * <p>공급자 쪽 수치는 조금씩 움직인다. 법정동 조회로 바꾼 직후(#46) 실측은 4,746
     * 이었다. 두 값 중 어느 쪽이 맞다기보다 잰 날이 다르다. 테스트가 붙드는 것은 정확한
     * 수가 아니라 상한을 넘는다는 사실이므로, 여기서는 최근 실측값을 쓴다.
     */
    private static final int GANGWON_CATALOG_SIZE = 4_741;

    private static final String GANGNEUNG_LAWD = "51150";
    private static final String SOKCHO_LAWD = "51210";
    private static final String GANGNEUNG_SIGUNGU = "1";
    private static final String SOKCHO_SIGUNGU = "5";

    private static final LocalDateTime CATALOG_CHANGED_AT = LocalDateTime.of(2026, 9, 18, 3, 0);

    /** 마지막으로 적재를 마친 시각. 내용이 바뀐 시각보다 뒤다 — 그 사이 재적재가 있었다(#69). */
    private static final LocalDateTime CATALOG_IMPORTED_AT = LocalDateTime.of(2026, 9, 19, 2, 0);
    private static final LocalDateTime PROVIDER_COLLECTED_AT = LocalDateTime.of(2026, 9, 19, 9, 0);

    /** 지금 활성인 스냅샷들. 캐시 키가 이것으로 값의 나이를 가른다(#71). */
    private static final ActiveSignalVersions SIGNAL_VERSIONS =
            new ActiveSignalVersions(11L, 22L, 33L);

    private AttractionService attractionService;
    private AttractionRepository attractionRepository;
    private AttractionCatalogImportRepository catalogImportRepository;
    private KorServiceClient korServiceClient;
    private ExternalApiCacheService cacheService;
    private RegionCodeRepository regionCodeRepository;
    private CenterRankService centerRankService;
    private SignalLookupService signalLookupService;
    private VisitTimingService visitTimingService;
    private PlaceImageLookupService placeImageLookupService;

    private final Map<String, Long> mentionCounts = new HashMap<>();
    private final Map<String, MentionStatus> mentionStatuses = new HashMap<>();

    @BeforeEach
    void setUp() {
        attractionRepository = Mockito.mock(AttractionRepository.class);
        catalogImportRepository = Mockito.mock(AttractionCatalogImportRepository.class);
        korServiceClient = Mockito.mock(KorServiceClient.class);
        cacheService = Mockito.mock(ExternalApiCacheService.class);
        regionCodeRepository = Mockito.mock(RegionCodeRepository.class);
        centerRankService = Mockito.mock(CenterRankService.class);
        signalLookupService = Mockito.mock(SignalLookupService.class);
        visitTimingService = Mockito.mock(VisitTimingService.class);

        placeImageLookupService = Mockito.mock(PlaceImageLookupService.class);
        given(placeImageLookupService.findUsable(any(java.util.Collection.class)))
                .willReturn(Map.of());

        attractionService = new AttractionService(korServiceClient, cacheService,
                regionCodeRepository, centerRankService, signalLookupService,
                visitTimingService, attractionRepository, catalogImportRepository,
                placeImageLookupService);

        given(regionCodeRepository.findAllByAreaCode("32"))
                .willReturn(List.of(region(GANGNEUNG_LAWD, GANGNEUNG_SIGUNGU, "강릉시"),
                        region(SOKCHO_LAWD, SOKCHO_SIGUNGU, "속초시")));
        given(regionCodeRepository.findByAreaCodeAndSigunguCode(anyString(), anyString()))
                .willReturn(Optional.empty());
        given(regionCodeRepository.findByAreaCodeAndSigunguCode("32", GANGNEUNG_SIGUNGU))
                .willReturn(Optional.of(region(GANGNEUNG_LAWD, GANGNEUNG_SIGUNGU, "강릉시")));
        given(regionCodeRepository.findByAreaCodeAndSigunguCode("32", SOKCHO_SIGUNGU))
                .willReturn(Optional.of(region(SOKCHO_LAWD, SOKCHO_SIGUNGU, "속초시")));

        given(centerRankService.resolveRanks(anyString(), any())).willReturn(Map.of());
        given(signalLookupService.findTmapRanks(any())).willReturn(Map.of());
        given(signalLookupService.findVisitorStats(any())).willReturn(Optional.empty());
        given(signalLookupService.findMentionRuleVersion()).willReturn(Optional.of("name+sigungu"));
        given(signalLookupService.activeSignalVersions()).willReturn(SIGNAL_VERSIONS);
        given(signalLookupService.findOnlineMentions(any())).willAnswer(invocation -> {
            Collection<String> contentIds = invocation.getArgument(0);
            Map<String, OnlineMentionView> views = new HashMap<>();

            for (String contentId : contentIds) {
                MentionStatus status = mentionStatuses.getOrDefault(contentId, MentionStatus.COLLECTED);
                Long count = status == MentionStatus.COLLECTED ? mentionCounts.get(contentId) : null;

                if (count == null && status == MentionStatus.COLLECTED) {
                    continue;
                }

                views.put(contentId, new OnlineMentionView(status, count,
                        LocalDateTime.of(2026, 9, 10, 0, 0), "name+sigungu"));
            }

            return Optional.of(views);
        });
        given(visitTimingService.resolve(any(), any(), any())).willReturn(Map.of());

        given(attractionRepository.findLatestCatalogChangeAt()).willReturn(CATALOG_CHANGED_AT);
        given(catalogImportRepository.findLatestCompletedAt()).willReturn(CATALOG_IMPORTED_AT);
    }

    // --- 카탈로그 전수 정렬 ---------------------------------------------------

    @Test
    @DisplayName("시·군을 지정하지 않은 언급량 정렬은 카탈로그 전수를 대상으로 한다")
    void sortsWholeCatalogBeyondOldProviderCap() {
        givenCatalog(catalogOf(GANGWON_CATALOG_SIZE));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        // 예전 상한 3,000 에서 잘렸다면 여기가 3,000 이 된다.
        assertThat(response.totalCount()).isEqualTo(GANGWON_CATALOG_SIZE);
        assertThat(response.items().get(0).contentId()).isEqualTo(contentId(GANGWON_CATALOG_SIZE));
        assertThat(response.items().get(0).onlineMention().count()).isEqualTo(GANGWON_CATALOG_SIZE);
    }

    @Test
    @DisplayName("카탈로그 정렬은 공급자를 부르지 않는다")
    void catalogSortCallsNoProvider() {
        givenCatalog(catalogOf(GANGWON_CATALOG_SIZE));

        attractionService.search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        verifyNoInteractions(cacheService);
        verify(korServiceClient, never()).areaBasedListByLawdJson(
                anyString(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("상한을 넘는 카탈로그에서도 언급 적은 순 상단은 실제 최소값이다")
    void ascendingTopIsTheRealMinimum() {
        givenCatalog(catalogOf(GANGWON_CATALOG_SIZE));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_ASC, 1, 20));

        assertThat(response.items().get(0).onlineMention().count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("두 정렬 방향의 대상 집합이 카탈로그 전수로 같다")
    void bothDirectionsCoverTheSameSet() {
        givenCatalog(catalogOf(GANGWON_CATALOG_SIZE));

        Set<String> descending = allContentIds(AttractionSort.ONLINE_MENTION_DESC);
        Set<String> ascending = allContentIds(AttractionSort.ONLINE_MENTION_ASC);

        assertThat(descending).isEqualTo(ascending);
        assertThat(descending).hasSize(GANGWON_CATALOG_SIZE);
    }

    @Test
    @DisplayName("페이지를 넘겨도 항목이 겹치거나 빠지지 않는다")
    void pagesAreContiguous() {
        givenCatalog(catalogOf(50));

        List<AttractionResponse> first = attractionService
                .search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20)).items();
        List<AttractionResponse> second = attractionService
                .search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 2, 20)).items();

        assertThat(first).hasSize(20);
        assertThat(second).hasSize(20);
        assertThat(first.get(19).onlineMention().count())
                .isGreaterThan(second.get(0).onlineMention().count());
    }

    @Test
    @DisplayName("모호·대상 아님은 응답에 그 상태 그대로 실린다")
    void ambiguousAndUnavailableReachTheResponse() {
        // 수집 실패로 덮으면 프론트는 "호출이 실패했다" 고 읽는다. 실제로는 이름이 모호해
        // 뺀 것이거나 애초에 수집 대상이 아닌 것이다(#94).
        mentionStatuses.put(contentId(1), MentionStatus.AMBIGUOUS);
        mentionStatuses.put(contentId(2), MentionStatus.UNAVAILABLE);
        givenCatalog(catalogOf(3));

        List<AttractionResponse> items =
                attractionService.search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20)).items();

        assertThat(items).filteredOn(item -> item.contentId().equals(contentId(1)))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.onlineMention().status()).isEqualTo(MentionStatus.AMBIGUOUS);
                    assertThat(item.onlineMention().count()).isNull();
                });
        assertThat(items).filteredOn(item -> item.contentId().equals(contentId(2)))
                .singleElement()
                .satisfies(item ->
                        assertThat(item.onlineMention().status()).isEqualTo(MentionStatus.UNAVAILABLE));
    }

    @Test
    @DisplayName("스냅샷에 없는 장소만 수집 실패로 남는다")
    void onlyPlacesMissingFromTheSnapshotAreCollectionFailed() {
        // 스냅샷에는 있는데 상태가 다른 것과, 스냅샷에 아예 없는 것은 다른 사실이다.
        mentionStatuses.put(contentId(1), MentionStatus.AMBIGUOUS);
        givenCatalog(catalogOf(2));
        // 스냅샷에 담기지 않은 장소. 상태가 다른 것이 아니라 조회 결과에 아예 없다.
        mentionCounts.remove(contentId(2));

        List<AttractionResponse> items =
                attractionService.search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20)).items();

        assertThat(items).filteredOn(item -> item.contentId().equals(contentId(2)))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.onlineMention().status())
                            .isEqualTo(MentionStatus.COLLECTION_FAILED);
                    assertThat(item.onlineMention().ruleVersion()).isEqualTo("name+sigungu");
                });
    }

    @Test
    @DisplayName("미산정·모호한 장소는 두 방향 모두 뒤쪽에 남는다")
    void unsortableStayAtTheBackInBothDirections() {
        mentionStatuses.put(contentId(2), MentionStatus.AMBIGUOUS);
        mentionStatuses.put(contentId(3), MentionStatus.COLLECTION_FAILED);
        givenCatalog(catalogOf(4));

        for (AttractionSort sort : AttractionSort.values()) {
            List<AttractionResponse> items =
                    attractionService.search(sortRequest(sort, 1, 20)).items();

            assertThat(items).hasSize(4);
            assertThat(items.subList(2, 4)).extracting(AttractionResponse::contentId)
                    .containsExactlyInAnyOrder(contentId(2), contentId(3));
        }
    }

    // --- 카탈로그가 비어 있을 때 -------------------------------------------------

    @Test
    @DisplayName("카탈로그를 아직 적재하지 않았으면 공급자 응답으로 정렬한다")
    void fallsBackToProviderWhenCatalogNeverImported() {
        given(attractionRepository.findLatestCatalogChangeAt()).willReturn(null);
        givenProviderPage(providerItem("9001", "속초해변"), providerItem("9002", "경포해변"));
        mentionCounts.put("9001", 170_653L);
        mentionCounts.put("9002", 140_006L);

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        assertThat(response.dataStatus()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(response.collectedAt()).isEqualTo(PROVIDER_COLLECTED_AT);
        assertThat(response.items()).extracting(AttractionResponse::contentId)
                .containsExactly("9001", "9002");
        verify(attractionRepository, never()).findAllWithRegion();
    }

    @Test
    @DisplayName("카탈로그가 비어 있고 공급자도 답하지 않으면 NO_DATA 로 알린다")
    void reportsNoDataWhenCatalogEmptyAndProviderSilent() {
        given(attractionRepository.findLatestCatalogChangeAt()).willReturn(null);
        given(korServiceClient.areaBasedListByLawdKey(anyString(), any(), any(), anyInt(), anyInt()))
                .willReturn("key");
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.noData());

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        assertThat(response.dataStatus()).isEqualTo(DataStatus.NO_DATA);
        assertThat(response.items()).isEmpty();
        assertThat(response.sort()).isEqualTo(AttractionSort.ONLINE_MENTION_DESC);
    }

    @Test
    @DisplayName("카탈로그는 있는데 조건에 맞는 장소가 없으면 NO_DATA 가 아니라 빈 목록이다")
    void emptyMatchIsAvailableNotNoData() {
        givenCatalog(catalogOf(3));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, "39", 1, 20, AttractionSort.ONLINE_MENTION_DESC, null, null,
                null, null, null, null));

        assertThat(response.dataStatus()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isZero();
    }

    // --- 조회 조건 조합 ---------------------------------------------------------

    @Test
    @DisplayName("시·군을 지정하면 그 시·군 카탈로그만 정렬한다")
    void sigunguNarrowsCatalogToThatRegion() {
        List<Attraction> sokcho = List.of(
                attraction("7001", "속초해변", SOKCHO_LAWD, "12",
                        new BigDecimal("38.2000000"), new BigDecimal("128.6000000")));
        given(attractionRepository.findAllWithRegionByLawdCode(SOKCHO_LAWD)).willReturn(sokcho);
        mentionCounts.put("7001", 170_653L);

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                SOKCHO_SIGUNGU, null, 1, 20, AttractionSort.ONLINE_MENTION_DESC, null, null,
                null, null, null, null));

        assertThat(response.items()).extracting(AttractionResponse::contentId).containsExactly("7001");
        assertThat(response.items().get(0).regionName()).isEqualTo("속초시");
        verify(attractionRepository, never()).findAllWithRegion();
    }

    @Test
    @DisplayName("매핑이 없는 시·군구 코드는 카탈로그 정렬에서도 거절한다")
    void rejectsUnknownSigunguCode() {
        givenCatalog(catalogOf(3));

        assertThatThrownBy(() -> attractionService.search(new AttractionSearchRequest(
                "99", null, 1, 20, AttractionSort.ONLINE_MENTION_DESC, null, null,
                null, null, null, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("분류를 지정하면 그 분류만 정렬 대상이다")
    void contentTypeFilterIsKept() {
        given(attractionRepository.findAllWithRegion()).willReturn(List.of(
                attraction("1", "관광지", GANGNEUNG_LAWD, "12",
                        new BigDecimal("37.8"), new BigDecimal("128.9")),
                attraction("2", "음식점", GANGNEUNG_LAWD, "39",
                        new BigDecimal("37.8"), new BigDecimal("128.9"))));
        mentionCounts.put("1", 10L);
        mentionCounts.put("2", 20L);

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, "12", 1, 20, AttractionSort.ONLINE_MENTION_DESC, null, null,
                null, null, null, null));

        assertThat(response.items()).extracting(AttractionResponse::contentId).containsExactly("1");
        assertThat(response.totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("경계 위의 좌표는 담고, 밖이거나 좌표가 없는 장소는 뺀다")
    void boundsIncludeEdgeAndExcludeMissingCoordinates() {
        given(attractionRepository.findAllWithRegion()).willReturn(List.of(
                attraction("edge", "경계 위", GANGNEUNG_LAWD, "12",
                        new BigDecimal("37.6"), new BigDecimal("128.7")),
                attraction("inside", "경계 안", GANGNEUNG_LAWD, "12",
                        new BigDecimal("37.7"), new BigDecimal("128.8")),
                attraction("outside", "경계 밖", GANGNEUNG_LAWD, "12",
                        new BigDecimal("37.95"), new BigDecimal("128.8")),
                attraction("noCoord", "좌표 없음", GANGNEUNG_LAWD, "12", null, null)));
        mentionCounts.put("edge", 1L);
        mentionCounts.put("inside", 2L);
        mentionCounts.put("outside", 3L);
        mentionCounts.put("noCoord", 4L);

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, null, 1, 20, AttractionSort.ONLINE_MENTION_DESC, null, null,
                new BigDecimal("37.6"), new BigDecimal("37.9"),
                new BigDecimal("128.7"), new BigDecimal("129.0")));

        assertThat(response.items()).extracting(AttractionResponse::contentId)
                .containsExactly("inside", "edge");
        assertThat(response.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("정렬 없이 경계만 준 요청도 카탈로그를 대상으로 하고 정렬 기준은 비워 둔다")
    void boundsOnlyRequestAlsoReadsCatalog() {
        given(attractionRepository.findAllWithRegion()).willReturn(List.of(
                attraction("inside", "경계 안", GANGNEUNG_LAWD, "12",
                        new BigDecimal("37.7"), new BigDecimal("128.8")),
                attraction("outside", "경계 밖", GANGNEUNG_LAWD, "12",
                        new BigDecimal("40.0"), new BigDecimal("128.8"))));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, null, 1, 20, null, null, null,
                new BigDecimal("37.6"), new BigDecimal("37.9"),
                new BigDecimal("128.7"), new BigDecimal("129.0")));

        assertThat(response.sort()).isNull();
        assertThat(response.items()).extracting(AttractionResponse::contentId).containsExactly("inside");
        verifyNoInteractions(cacheService);
    }

    @Test
    @DisplayName("날짜 탐색을 함께 요청하면 카탈로그 정렬 결과에도 visitTiming 이 붙는다")
    void attachesVisitTimingToCatalogSortedItems() {
        givenCatalog(catalogOf(2));
        LocalDate visitDate = LocalDate.of(2026, 9, 20);
        given(visitTimingService.resolve(any(), any(), any())).willReturn(Map.of(
                contentId(2), new VisitTiming(DateMode.FIXED, VisitTimingStatus.LOW, visitDate,
                        null, 30, visitDate.minusDays(1), visitDate.plusDays(29),
                        DataStatus.AVAILABLE, PROVIDER_COLLECTED_AT, "TatsCnctrRateService")));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, null, 1, 20, AttractionSort.ONLINE_MENTION_DESC, DateMode.FIXED, visitDate,
                null, null, null, null));

        assertThat(response.items().get(0).contentId()).isEqualTo(contentId(2));
        assertThat(response.items().get(0).visitTiming().status()).isEqualTo(VisitTimingStatus.LOW);
    }

    // --- 응답 계약 -------------------------------------------------------------

    @Test
    @DisplayName("카탈로그 정렬도 목록 응답 계약을 그대로 지킨다")
    void keepsListResponseContract() {
        givenCatalog(catalogOf(3));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        assertThat(response.sort()).isEqualTo(AttractionSort.ONLINE_MENTION_DESC);
        assertThat(response.sortApplied()).isTrue();
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.dataStatus()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(response.source()).isEqualTo("KorService2");
        // 카탈로그를 마지막으로 적재한 시각. 서버가 응답을 만든 시각이 아니다.
        assertThat(response.collectedAt()).isEqualTo(CATALOG_IMPORTED_AT);

        AttractionResponse item = response.items().get(0);
        assertThat(item.regionName()).isEqualTo("강릉시");
        assertThat(item.lawdCode()).isEqualTo(GANGNEUNG_LAWD);
        assertThat(item.onlineMention().status()).isEqualTo(MentionStatus.COLLECTED);
        assertThat(item.tmapRank()).isNotNull();
        assertThat(item.visitorStats()).isNotNull();
    }

    @Test
    @DisplayName("정렬도 경계도 없는 요청은 지금처럼 공급자 페이지를 그대로 쓴다")
    void plainPageStillUsesProvider() {
        givenProviderPage(providerItem("9001", "속초해변"));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, null, 1, 20, null, null, null, null, null, null, null));

        assertThat(response.items()).extracting(AttractionResponse::contentId).containsExactly("9001");
        verify(attractionRepository, never()).findAllWithRegion();
        verify(attractionRepository, never()).findLatestCatalogChangeAt();
    }

    // --- 페이지 경계 -----------------------------------------------------------

    @Test
    @DisplayName("마지막 페이지는 남은 수만큼만 담는다")
    void lastPageHoldsTheRemainder() {
        givenCatalog(catalogOf(50));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 3, 20));

        assertThat(response.items()).hasSize(10);
        assertThat(response.totalCount()).isEqualTo(50);
        assertThat(response.page()).isEqualTo(3);
    }

    @Test
    @DisplayName("마지막 페이지가 정확히 들어맞으면 다음 페이지는 비어 있다")
    void pageAfterAnExactFitIsEmpty() {
        givenCatalog(catalogOf(40));

        assertThat(attractionService.search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 2, 20))
                .items()).hasSize(20);

        AttractionListResponse beyond = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 3, 20));

        assertThat(beyond.items()).isEmpty();
        // 대상 집합의 크기는 그대로다. 페이지를 넘겼다고 0 이 되지 않는다.
        assertThat(beyond.totalCount()).isEqualTo(40);
    }

    @Test
    @DisplayName("범위를 한참 넘긴 페이지도 오류가 아니라 빈 목록이다")
    void farBeyondLastPageIsEmptyNotAnError() {
        givenCatalog(catalogOf(3));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 100, 20));

        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.dataStatus()).isEqualTo(DataStatus.AVAILABLE);
    }

    @Test
    @DisplayName("한 페이지 크기가 전체보다 크면 한 번에 다 담는다")
    void singlePageHoldsEverything() {
        givenCatalog(catalogOf(7));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 100));

        assertThat(response.items()).hasSize(7);
        assertThat(response.totalCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("페이지와 개수를 주지 않으면 1쪽 20건으로 본다")
    void defaultsToFirstPageOfTwenty() {
        givenCatalog(catalogOf(30));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, null, null, null, AttractionSort.ONLINE_MENTION_DESC, null, null,
                null, null, null, null));

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.items()).hasSize(20);
    }

    // --- 정렬 없는 공급자 조회 ---------------------------------------------------

    @Test
    @DisplayName("정렬 없는 조회의 전체 건수는 공급자가 말한 값이다")
    void plainPageReportsProviderTotalCount() {
        givenProviderResponse(676, providerItem("9001", "속초해변"), providerItem("9002", "경포해변"));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, null, 2, 20, null, null, null, null, null, null, null));

        // 이 페이지에 담긴 수가 아니라 조회 범위 전체의 크기다.
        assertThat(response.totalCount()).isEqualTo(676);
        assertThat(response.items()).hasSize(2);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.sort()).isNull();
    }

    @Test
    @DisplayName("정렬 없는 조회는 요청한 페이지를 그대로 공급자에게 넘긴다")
    void plainPagePassesPagingToProvider() {
        givenProviderPage(providerItem("9001", "속초해변"));

        attractionService.search(new AttractionSearchRequest(
                null, null, 3, 50, null, null, null, null, null, null, null));

        verify(korServiceClient).areaBasedListByLawdKey("51", null, null, 3, 50);
    }

    @Test
    @DisplayName("시·군을 지정하면 법정동 시·군구 코드 3자리로 공급자를 부른다")
    void plainPageAsksProviderByLawdSigunguCode() {
        givenProviderPage(providerItem("9001", "속초해변"));

        attractionService.search(new AttractionSearchRequest(
                SOKCHO_SIGUNGU, "12", 1, 20, null, null, null, null, null, null, null));

        // 공개 파라미터는 관광공사 코드(5)지만 공급자에게는 법정동 코드(51210 의 뒤 3자리)로 묻는다.
        verify(korServiceClient).areaBasedListByLawdKey("51", "210", "12", 1, 20);
    }

    @Test
    @DisplayName("공급자 갱신에 실패해 최종 정상 데이터로 응답하면 STALE 로 알린다")
    void plainPagePropagatesStale() {
        given(korServiceClient.areaBasedListByLawdKey(anyString(), any(), any(), anyInt(), anyInt()))
                .willReturn("key");
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.stale("{}", PROVIDER_COLLECTED_AT));
        given(korServiceClient.parse(anyString(), anyString()))
                .willReturn(providerResponse(1, providerItem("9001", "속초해변")));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, null, 1, 20, null, null, null, null, null, null, null));

        assertThat(response.dataStatus()).isEqualTo(DataStatus.STALE);
        assertThat(response.collectedAt()).isEqualTo(PROVIDER_COLLECTED_AT);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    @DisplayName("캐시에 남은 본문을 더 이상 해석할 수 없으면 빈 목록이 아니라 NO_DATA 다")
    void unparseableCachedBodyIsNoData() {
        given(korServiceClient.areaBasedListByLawdKey(anyString(), any(), any(), anyInt(), anyInt()))
                .willReturn("key");
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available("<html>", PROVIDER_COLLECTED_AT));
        given(korServiceClient.parse(anyString(), anyString()))
                .willThrow(new ExternalApiException(ApiProvider.KOR_SERVICE2, "해석 실패"));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, null, 1, 20, null, null, null, null, null, null, null));

        assertThat(response.dataStatus()).isEqualTo(DataStatus.NO_DATA);
        assertThat(response.items()).isEmpty();
        assertThat(response.collectedAt()).isNull();
    }

    @Test
    @DisplayName("시·군을 지정한 조회에만 중심관광지 순위를 붙인다")
    void centerRankOnlyWithSigungu() {
        givenProviderPage(providerItem("9001", "속초해변"));
        given(centerRankService.resolveRanks(eq(SOKCHO_LAWD), any())).willReturn(Map.of("9001", 2));

        AttractionListResponse withSigungu = attractionService.search(new AttractionSearchRequest(
                SOKCHO_SIGUNGU, null, 1, 20, null, null, null, null, null, null, null));
        AttractionListResponse withoutSigungu = attractionService.search(new AttractionSearchRequest(
                null, null, 1, 20, null, null, null, null, null, null, null));

        assertThat(withSigungu.items().get(0).centerRank()).isEqualTo(2);
        // 여러 시·군이 섞인 목록에서 각 시·군 안의 순위를 한 줄에 놓으면 시·군 사이의 순위처럼 읽힌다.
        assertThat(withoutSigungu.items().get(0).centerRank()).isNull();
    }

    // --- 날짜 탐색 조합 ---------------------------------------------------------

    @Test
    @DisplayName("날짜 탐색을 요청하지 않으면 모드를 비운 채 넘기고 항목에도 붙지 않는다")
    void withoutDateModeNothingIsAttached() {
        givenCatalog(catalogOf(2));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        verify(visitTimingService).resolve(any(), eq((DateMode) null), eq((LocalDate) null));
        assertThat(response.items().get(0).visitTiming()).isNull();
    }

    @Test
    @DisplayName("날짜를 정하지 않은 유연 탐색도 그대로 넘긴다")
    void flexibleDateModeIsPassedThrough() {
        givenCatalog(catalogOf(2));

        attractionService.search(new AttractionSearchRequest(
                null, null, 1, 20, AttractionSort.ONLINE_MENTION_DESC, DateMode.FLEXIBLE, null,
                null, null, null, null));

        verify(visitTimingService).resolve(any(), eq(DateMode.FLEXIBLE), eq((LocalDate) null));
    }

    @Test
    @DisplayName("정렬 없는 공급자 조회에도 날짜 탐색 결과가 붙는다")
    void plainPageAlsoCarriesVisitTiming() {
        givenProviderPage(providerItem("9001", "속초해변"));
        LocalDate visitDate = LocalDate.of(2026, 9, 20);
        given(visitTimingService.resolve(any(), any(), any())).willReturn(Map.of(
                "9001", new VisitTiming(DateMode.FIXED, VisitTimingStatus.LOW, visitDate,
                        null, 30, visitDate.minusDays(1), visitDate.plusDays(29),
                        DataStatus.AVAILABLE, PROVIDER_COLLECTED_AT, "TatsCnctrRateService")));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, null, 1, 20, null, DateMode.FIXED, visitDate, null, null, null, null));

        assertThat(response.items().get(0).visitTiming().status()).isEqualTo(VisitTimingStatus.LOW);
    }

    @Test
    @DisplayName("예측을 얻지 못한 장소는 visitTiming 이 비어 있고 나머지 항목은 그대로다")
    void placesWithoutForecastKeepTheRestOfTheRow() {
        givenCatalog(catalogOf(2));
        LocalDate visitDate = LocalDate.of(2026, 9, 20);
        given(visitTimingService.resolve(any(), any(), any())).willReturn(Map.of(
                contentId(2), new VisitTiming(DateMode.FIXED, VisitTimingStatus.LOW, visitDate,
                        null, 30, visitDate.minusDays(1), visitDate.plusDays(29),
                        DataStatus.AVAILABLE, PROVIDER_COLLECTED_AT, "TatsCnctrRateService")));

        List<AttractionResponse> items = attractionService.search(new AttractionSearchRequest(
                null, null, 1, 20, AttractionSort.ONLINE_MENTION_DESC, DateMode.FIXED, visitDate,
                null, null, null, null)).items();

        assertThat(items.get(1).contentId()).isEqualTo(contentId(1));
        assertThat(items.get(1).visitTiming()).isNull();
        assertThat(items.get(1).onlineMention().count()).isEqualTo(1L);
    }

    // --- 경계와 분류를 함께 준 조회 -----------------------------------------------

    @Test
    @DisplayName("경계와 분류를 함께 주면 둘을 모두 만족하는 장소만 남는다")
    void boundsAndContentTypeAreBothApplied() {
        given(attractionRepository.findAllWithRegion()).willReturn(List.of(
                attraction("both", "경계 안 관광지", GANGNEUNG_LAWD, "12",
                        new BigDecimal("37.7"), new BigDecimal("128.8")),
                attraction("typeOnly", "경계 밖 관광지", GANGNEUNG_LAWD, "12",
                        new BigDecimal("40.0"), new BigDecimal("128.8")),
                attraction("boundsOnly", "경계 안 음식점", GANGNEUNG_LAWD, "39",
                        new BigDecimal("37.7"), new BigDecimal("128.8")),
                attraction("neither", "경계 밖 음식점", GANGNEUNG_LAWD, "39",
                        new BigDecimal("40.0"), new BigDecimal("128.8"))));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, "12", 1, 20, null, null, null,
                new BigDecimal("37.6"), new BigDecimal("37.9"),
                new BigDecimal("128.7"), new BigDecimal("129.0")));

        assertThat(response.items()).extracting(AttractionResponse::contentId)
                .containsExactly("both");
        assertThat(response.totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("시·군과 경계와 분류를 한꺼번에 줘도 조건이 하나도 빠지지 않는다")
    void sigunguBoundsAndContentTypeStack() {
        given(attractionRepository.findAllWithRegionByLawdCode(SOKCHO_LAWD)).willReturn(List.of(
                attraction("keep", "속초 관광지", SOKCHO_LAWD, "12",
                        new BigDecimal("38.2"), new BigDecimal("128.6")),
                attraction("wrongType", "속초 음식점", SOKCHO_LAWD, "39",
                        new BigDecimal("38.2"), new BigDecimal("128.6")),
                attraction("outOfBounds", "속초 먼 관광지", SOKCHO_LAWD, "12",
                        new BigDecimal("38.9"), new BigDecimal("128.6"))));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                SOKCHO_SIGUNGU, "12", 1, 20, null, null, null,
                new BigDecimal("38.1"), new BigDecimal("38.3"),
                new BigDecimal("128.5"), new BigDecimal("128.7")));

        assertThat(response.items()).extracting(AttractionResponse::contentId).containsExactly("keep");
        verify(attractionRepository, never()).findAllWithRegion();
    }

    // --- 공급자 폴백 -----------------------------------------------------------

    @Test
    @DisplayName("폴백은 조회 범위의 모든 페이지를 모은다")
    void fallbackCollectsEveryPage() {
        given(attractionRepository.findLatestCatalogChangeAt()).willReturn(null);
        given(korServiceClient.areaBasedListByLawdKey(anyString(), any(), any(), anyInt(), anyInt()))
                .willReturn("key");
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available("{}", PROVIDER_COLLECTED_AT));
        given(korServiceClient.parse(anyString(), anyString())).willReturn(
                providerResponse(150, providerItems(1, 100)),
                providerResponse(150, providerItems(101, 150)));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        assertThat(response.totalCount()).isEqualTo(150);
        verify(korServiceClient).areaBasedListByLawdKey("51", null, null, 1, 100);
        verify(korServiceClient).areaBasedListByLawdKey("51", null, null, 2, 100);
    }

    @Test
    @DisplayName("폴백에서 한 페이지라도 최종 정상 데이터면 전체를 STALE 로 알린다")
    void fallbackReportsStaleIfAnyPageIsStale() {
        given(attractionRepository.findLatestCatalogChangeAt()).willReturn(null);
        given(korServiceClient.areaBasedListByLawdKey(anyString(), any(), any(), anyInt(), anyInt()))
                .willReturn("key");
        given(cacheService.fetch(any(), anyString(), any(), any())).willReturn(
                CachedResponse.available("{}", PROVIDER_COLLECTED_AT),
                CachedResponse.stale("{}", PROVIDER_COLLECTED_AT.minusDays(1)));
        given(korServiceClient.parse(anyString(), anyString())).willReturn(
                providerResponse(150, providerItems(1, 100)),
                providerResponse(150, providerItems(101, 150)));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        assertThat(response.dataStatus()).isEqualTo(DataStatus.STALE);
        // 기준 시점은 첫 페이지를 받은 시각이다.
        assertThat(response.collectedAt()).isEqualTo(PROVIDER_COLLECTED_AT);
    }

    @Test
    @DisplayName("폴백 도중 공급자가 답하지 않으면 거기까지 모은 것으로 응답한다")
    void fallbackStopsWhenProviderGoesSilent() {
        given(attractionRepository.findLatestCatalogChangeAt()).willReturn(null);
        given(korServiceClient.areaBasedListByLawdKey(anyString(), any(), any(), anyInt(), anyInt()))
                .willReturn("key");
        given(cacheService.fetch(any(), anyString(), any(), any())).willReturn(
                CachedResponse.available("{}", PROVIDER_COLLECTED_AT),
                CachedResponse.noData());
        given(korServiceClient.parse(anyString(), anyString()))
                .willReturn(providerResponse(150, providerItems(1, 100)));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        // 150 을 채우지 못했어도 모은 만큼은 그대로 쓴다. 빈 응답으로 되돌리지 않는다.
        assertThat(response.totalCount()).isEqualTo(100);
        assertThat(response.dataStatus()).isEqualTo(DataStatus.AVAILABLE);
    }

    @Test
    @DisplayName("폴백에서도 경계 밖 장소는 걸러진다")
    void fallbackFiltersByBounds() {
        given(attractionRepository.findLatestCatalogChangeAt()).willReturn(null);
        givenProviderResponse(2,
                providerItem("inside", "경계 안", "37.7500000", "128.8500000"),
                providerItem("outside", "경계 밖", "40.0000000", "128.8500000"));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, null, 1, 20, null, null, null,
                new BigDecimal("37.6"), new BigDecimal("37.9"),
                new BigDecimal("128.7"), new BigDecimal("129.0")));

        assertThat(response.items()).extracting(AttractionResponse::contentId)
                .containsExactly("inside");
        assertThat(response.totalCount()).isEqualTo(1);
    }

    /**
     * 언급량 스냅샷이 없으면 정렬 기준이 없다. 그때는 순서를 만들어내지 않고 공급자 순서를
     * 그대로 쓴다(#65).
     *
     * <p>예전에는 정렬 불가 항목을 이름 오름차순으로 다시 줄 세웠다. 응답의 {@code sort} 는
     * 요청한 기준을 그대로 싣고 있어서, 프론트에서는 가나다순 목록이 언급량 순으로 읽혔다.
     */
    @Test
    @DisplayName("폴백인데 언급량 스냅샷이 없으면 공급자 순서를 그대로 둔다")
    void fallbackKeepsProviderOrderWithoutMentionSnapshot() {
        given(attractionRepository.findLatestCatalogChangeAt()).willReturn(null);
        // 이미 등록해 둔 answer 가 given(...) 안에서 실행되지 않도록 반대 순서로 덮는다.
        willReturn(Optional.empty()).given(signalLookupService).findOnlineMentions(any());
        given(signalLookupService.findMentionRuleVersion()).willReturn(Optional.empty());
        givenProviderResponse(3,
                providerItem("9001", "속초해변"),
                providerItem("9002", "경포해변"),
                providerItem("9003", "주문진해변"));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        // 공급자가 준 순서 그대로다. 이름순으로 다시 줄 세우지 않는다.
        assertThat(response.items()).extracting(AttractionResponse::name)
                .containsExactly("속초해변", "경포해변", "주문진해변");
        // 각 항목의 상태에 더해, 목록 수준에서도 정렬을 적용하지 못했다는 사실을 알린다.
        assertThat(response.items()).allSatisfy(item ->
                assertThat(item.onlineMention().status()).isEqualTo(MentionStatus.COLLECTION_FAILED));
        assertThat(response.sortApplied()).isFalse();
        // 요청한 기준은 그대로 반향한다. 무엇을 요청했는지는 프론트가 알아야 한다.
        assertThat(response.sort()).isEqualTo(AttractionSort.ONLINE_MENTION_DESC);
    }

    // --- 읽어 둔 카탈로그 다시 쓰기 (#71) -----------------------------------------

    @Test
    @DisplayName("같은 조건을 다시 물으면 카탈로그도 신호도 다시 읽지 않는다")
    void repeatedQueryReadsNothingAgain() {
        givenCatalog(catalogOf(50));
        attractionService.search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        Mockito.clearInvocations(attractionRepository, signalLookupService);
        AttractionListResponse second = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 2, 20));

        // 두 번째 페이지도 제대로 나온다. 읽기만 건너뛴 것이지 결과가 줄지 않는다.
        assertThat(second.items()).hasSize(20);
        assertThat(second.totalCount()).isEqualTo(50);
        assertThat(second.collectedAt()).isEqualTo(CATALOG_IMPORTED_AT);
        verifyNoInteractions(attractionRepository);
        verify(signalLookupService, never()).findOnlineMentions(any());
        verify(signalLookupService, never()).findTmapRanks(any());
        verify(signalLookupService, never()).findVisitorStats(any());
    }

    @Test
    @DisplayName("지도만 밀어 경계가 달라져도 읽어 둔 카탈로그를 다시 쓴다")
    void movingTheMapReusesTheSameRead() {
        given(attractionRepository.findAllWithRegion()).willReturn(List.of(
                attraction("west", "서쪽", GANGNEUNG_LAWD, "12",
                        new BigDecimal("37.7"), new BigDecimal("128.8")),
                attraction("east", "동쪽", GANGNEUNG_LAWD, "12",
                        new BigDecimal("37.7"), new BigDecimal("128.95"))));
        mentionCounts.put("west", 10L);
        mentionCounts.put("east", 20L);

        attractionService.search(boundsRequest(new BigDecimal("128.7"), new BigDecimal("128.9")));
        Mockito.clearInvocations(attractionRepository, signalLookupService);

        AttractionListResponse moved = attractionService.search(
                boundsRequest(new BigDecimal("128.9"), new BigDecimal("129.0")));

        assertThat(moved.items()).extracting(AttractionResponse::contentId).containsExactly("east");
        verifyNoInteractions(attractionRepository);
    }

    @Test
    @DisplayName("스냅샷을 교체하면 읽어 둔 값을 쓰지 않는다")
    void snapshotReplacementInvalidates() {
        givenCatalog(catalogOf(3));
        attractionService.search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        Mockito.clearInvocations(attractionRepository, signalLookupService);
        given(signalLookupService.activeSignalVersions())
                .willReturn(new ActiveSignalVersions(12L, 22L, 33L));
        attractionService.search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        verify(attractionRepository).findAllWithRegion();
        verify(signalLookupService).findOnlineMentions(any());
    }

    @Test
    @DisplayName("카탈로그를 다시 적재하면 읽어 둔 값을 쓰지 않는다")
    void reimportInvalidates() {
        givenCatalog(catalogOf(3));
        attractionService.search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        Mockito.clearInvocations(attractionRepository, signalLookupService);
        given(catalogImportRepository.findLatestCompletedAt())
                .willReturn(CATALOG_IMPORTED_AT.plusDays(1));
        AttractionListResponse after = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        verify(attractionRepository).findAllWithRegion();
        assertThat(after.collectedAt()).isEqualTo(CATALOG_IMPORTED_AT.plusDays(1));
    }

    @Test
    @DisplayName("조회 조건이 다르면 읽어 둔 값을 쓰지 않는다")
    void differentConditionsDoNotShareTheRead() {
        givenCatalog(catalogOf(3));
        attractionService.search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        Mockito.clearInvocations(attractionRepository);
        // 분류만 달라져도 대상이 다른 집합이다.
        attractionService.search(new AttractionSearchRequest(
                null, "12", 1, 20, AttractionSort.ONLINE_MENTION_DESC, null, null,
                null, null, null, null));

        verify(attractionRepository).findAllWithRegion();
    }

    @Test
    @DisplayName("적재 이력이 없으면 무효화 신호가 없어 읽어 두지 않는다")
    void doesNotCacheWithoutImportHistory() {
        given(catalogImportRepository.findLatestCompletedAt()).willReturn(null);
        givenCatalog(catalogOf(3));
        attractionService.search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        Mockito.clearInvocations(attractionRepository);
        attractionService.search(sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        verify(attractionRepository).findAllWithRegion();
    }

    // --- 기준 시점 (#69) ---------------------------------------------------------

    /**
     * 내용이 같은 재적재는 {@code attraction.modified_at} 을 밀지 않는다. 그래서 기준 시점은
     * 카탈로그 변경 시각이 아니라 마지막 적재 이력의 시각이다(#69).
     */
    @Test
    @DisplayName("카탈로그 조회의 기준 시점은 마지막으로 적재를 마친 시각이다")
    void collectedAtComesFromTheLastImport() {
        givenCatalog(catalogOf(3));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        assertThat(response.collectedAt()).isEqualTo(CATALOG_IMPORTED_AT);
        assertThat(response.collectedAt()).isNotEqualTo(CATALOG_CHANGED_AT);
    }

    @Test
    @DisplayName("적재 이력이 없으면 지금까지 쓰던 카탈로그 변경 시각을 그대로 쓴다")
    void collectedAtFallsBackToCatalogChangeWithoutHistory() {
        // 이 이력이 생기기 전에 적재한 환경. 카탈로그는 가득한데 이력이 한 줄도 없다.
        given(catalogImportRepository.findLatestCompletedAt()).willReturn(null);
        givenCatalog(catalogOf(3));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        assertThat(response.collectedAt()).isEqualTo(CATALOG_CHANGED_AT);
        // 이력이 없다고 공급자로 내려가지 않는다. 카탈로그가 비어 있는 것과 다르다.
        assertThat(response.items()).hasSize(3);
        verifyNoInteractions(cacheService);
    }

    @Test
    @DisplayName("카탈로그가 비어 있으면 적재 이력이 있어도 공급자 기준 시점을 쓴다")
    void providerFallbackKeepsProviderCollectedAt() {
        given(attractionRepository.findLatestCatalogChangeAt()).willReturn(null);
        givenProviderPage(providerItem("9001", "속초해변"));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        assertThat(response.collectedAt()).isEqualTo(PROVIDER_COLLECTED_AT);
    }

    // --- 정렬을 적용했는지 (#65) --------------------------------------------------

    @Test
    @DisplayName("카탈로그 정렬도 언급량 스냅샷이 없으면 카탈로그 기본 순서를 그대로 둔다")
    void catalogKeepsCatalogOrderWithoutMentionSnapshot() {
        willReturn(Optional.empty()).given(signalLookupService).findOnlineMentions(any());
        given(signalLookupService.findMentionRuleVersion()).willReturn(Optional.empty());
        given(attractionRepository.findAllWithRegion()).willReturn(List.of(
                attraction("9001", "속초해변", GANGNEUNG_LAWD, "12",
                        new BigDecimal("37.7"), new BigDecimal("128.8")),
                attraction("9002", "경포해변", GANGNEUNG_LAWD, "12",
                        new BigDecimal("37.7"), new BigDecimal("128.8"))));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        // 카탈로그 기본 순서는 표준 관광지명 오름차순이다. 요청한 정렬과는 무관하다.
        assertThat(response.items()).extracting(AttractionResponse::name)
                .containsExactly("경포해변", "속초해변");
        assertThat(response.sortApplied()).isFalse();
    }

    @Test
    @DisplayName("산정된 장소가 하나라도 있으면 정렬을 적용하고 그 사실을 알린다")
    void sortAppliedWhenAtLeastOnePlaceIsSortable() {
        mentionStatuses.put(contentId(1), MentionStatus.COLLECTION_FAILED);
        givenCatalog(catalogOf(2));

        AttractionListResponse response = attractionService.search(
                sortRequest(AttractionSort.ONLINE_MENTION_DESC, 1, 20));

        assertThat(response.sortApplied()).isTrue();
        // 산정된 장소가 앞에 서고, 산정되지 않은 장소는 뒤에 붙는다.
        assertThat(response.items()).extracting(AttractionResponse::contentId)
                .containsExactly(contentId(2), contentId(1));
    }

    @Test
    @DisplayName("정렬을 요청하지 않으면 적용할 정렬도 없다")
    void sortNotAppliedWhenNotRequested() {
        given(attractionRepository.findAllWithRegion()).willReturn(List.of(
                attraction("inside", "경계 안", GANGNEUNG_LAWD, "12",
                        new BigDecimal("37.7"), new BigDecimal("128.8"))));

        AttractionListResponse response = attractionService.search(new AttractionSearchRequest(
                null, null, 1, 20, null, null, null,
                new BigDecimal("37.6"), new BigDecimal("37.9"),
                new BigDecimal("128.7"), new BigDecimal("129.0")));

        assertThat(response.sort()).isNull();
        assertThat(response.sortApplied()).isFalse();
    }

    // --- 도우미 ---------------------------------------------------------------

    private Set<String> allContentIds(AttractionSort sort) {
        Set<String> collected = new LinkedHashSet<>();
        int size = 100;

        for (int page = 1; ; page++) {
            List<AttractionResponse> items = attractionService.search(sortRequest(sort, page, size)).items();

            if (items.isEmpty()) {
                return collected;
            }

            items.forEach(item -> collected.add(item.contentId()));
        }
    }

    private void givenCatalog(List<Attraction> catalog) {
        given(attractionRepository.findAllWithRegion()).willReturn(catalog);
    }

    /** 언급량이 식별자 번호와 같은 카탈로그. 순위를 눈으로 따라갈 수 있게 해 둔다. */
    private List<Attraction> catalogOf(int count) {
        List<Attraction> catalog = new ArrayList<>(count);

        for (int i = 1; i <= count; i++) {
            String contentId = contentId(i);
            catalog.add(attraction(contentId, "관광지" + contentId, GANGNEUNG_LAWD, "12",
                    new BigDecimal("37.7500000"), new BigDecimal("128.8500000")));
            mentionCounts.putIfAbsent(contentId, (long) i);
        }

        return catalog;
    }

    private static String contentId(int index) {
        return String.format("%06d", index);
    }

    private static Attraction attraction(String contentId, String name, String lawdCode,
                                         String contentTypeId,
                                         BigDecimal latitude, BigDecimal longitude) {
        return Attraction.builder()
                .contentId(contentId)
                .name(name)
                .address("강원특별자치도")
                .latitude(latitude)
                .longitude(longitude)
                .contentTypeId(contentTypeId)
                .regionCode(region(lawdCode, lawdCode.equals(GANGNEUNG_LAWD)
                        ? GANGNEUNG_SIGUNGU : SOKCHO_SIGUNGU,
                        lawdCode.equals(GANGNEUNG_LAWD) ? "강릉시" : "속초시"))
                .dataStatus(DataStatus.AVAILABLE)
                .baseAt(LocalDateTime.of(2026, 9, 1, 0, 0))
                .source("KorService2")
                .build();
    }

    private static RegionCode region(String lawdCode, String sigunguCode, String name) {
        return RegionCode.builder()
                .lawdCode(lawdCode)
                .areaCode("32")
                .sigunguCode(sigunguCode)
                .name(name)
                .build();
    }

    /** 공급자가 알려주는 전체 건수를 이 페이지의 건수와 다르게 둘 때 쓴다. */
    private void givenProviderResponse(int totalCount, KorServiceItem... items) {
        given(korServiceClient.areaBasedListByLawdKey(anyString(), any(), any(), anyInt(), anyInt()))
                .willReturn("key");
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available("{}", PROVIDER_COLLECTED_AT));
        given(korServiceClient.parse(anyString(), anyString()))
                .willReturn(providerResponse(totalCount, items));
    }

    private static KorServiceResponse providerResponse(int totalCount, KorServiceItem... items) {
        return new KorServiceResponse(new KorServiceResponse.Response(
                new KorServiceResponse.Header("0000", "OK"),
                new KorServiceResponse.Body(
                        new KorServiceResponse.Items(List.of(items)),
                        items.length, 1, totalCount)));
    }

    /** 식별자가 from..to 인 공급자 항목들. 언급량은 식별자 번호와 같게 맞춘다. */
    private KorServiceItem[] providerItems(int from, int to) {
        List<KorServiceItem> items = new ArrayList<>(to - from + 1);

        for (int i = from; i <= to; i++) {
            String contentId = contentId(i);
            items.add(providerItem(contentId, "관광지" + contentId));
            mentionCounts.putIfAbsent(contentId, (long) i);
        }

        return items.toArray(new KorServiceItem[0]);
    }

    private static KorServiceItem providerItem(String contentId, String name,
                                               String latitude, String longitude) {
        return new KorServiceItem(contentId, "12", name, "강원특별자치도", "",
                "", "", longitude, latitude, "", "", "51", "150",
                "", "20260901000000", "", "", "", "");
    }

    private void givenProviderPage(KorServiceItem... items) {
        given(korServiceClient.areaBasedListByLawdKey(anyString(), any(), any(), anyInt(), anyInt()))
                .willReturn("key");
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available("{}", PROVIDER_COLLECTED_AT));
        given(korServiceClient.parse(anyString(), anyString())).willReturn(new KorServiceResponse(
                new KorServiceResponse.Response(
                        new KorServiceResponse.Header("0000", "OK"),
                        new KorServiceResponse.Body(
                                new KorServiceResponse.Items(List.of(items)),
                                items.length, 1, items.length))));
    }

    private static KorServiceItem providerItem(String contentId, String name) {
        return new KorServiceItem(contentId, "12", name, "강원특별자치도", "",
                "", "", "128.8500000", "37.7500000", "", "", "51", "150",
                "", "20260901000000", "", "", "", "");
    }

    /** 경도만 움직이는 경계 조회. 지도를 옆으로 미는 것과 같다. */
    private static AttractionSearchRequest boundsRequest(BigDecimal minLongitude,
                                                         BigDecimal maxLongitude) {
        return new AttractionSearchRequest(null, null, 1, 20, null, null, null,
                new BigDecimal("37.6"), new BigDecimal("37.9"), minLongitude, maxLongitude);
    }

    private static AttractionSearchRequest sortRequest(AttractionSort sort, int page, int size) {
        return new AttractionSearchRequest(null, null, page, size, sort, null, null,
                null, null, null, null);
    }
}
