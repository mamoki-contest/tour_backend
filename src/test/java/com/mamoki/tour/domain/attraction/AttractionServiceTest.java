package com.mamoki.tour.domain.attraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.attraction.service.AttractionService;
import com.mamoki.tour.domain.attraction.service.CenterRankService;
import com.mamoki.tour.domain.attraction.service.SignalLookupService;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;
import com.mamoki.tour.domain.visittiming.enums.DateMode;
import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;
import com.mamoki.tour.domain.visittiming.service.VisitTimingService;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.enums.MentionStatus;
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

    /** 법정동 조회로 바꾼 뒤(#46) 실측한 강원 카탈로그 규모. 예전 정렬 상한 3,000 을 넘는다. */
    private static final int GANGWON_CATALOG_SIZE = 4_746;

    private static final String GANGNEUNG_LAWD = "51150";
    private static final String SOKCHO_LAWD = "51210";
    private static final String GANGNEUNG_SIGUNGU = "1";
    private static final String SOKCHO_SIGUNGU = "5";

    private static final LocalDateTime CATALOG_IMPORTED_AT = LocalDateTime.of(2026, 9, 18, 3, 0);
    private static final LocalDateTime PROVIDER_COLLECTED_AT = LocalDateTime.of(2026, 9, 19, 9, 0);

    private AttractionService attractionService;
    private AttractionRepository attractionRepository;
    private KorServiceClient korServiceClient;
    private ExternalApiCacheService cacheService;
    private RegionCodeRepository regionCodeRepository;
    private CenterRankService centerRankService;
    private SignalLookupService signalLookupService;
    private VisitTimingService visitTimingService;

    private final Map<String, Long> mentionCounts = new HashMap<>();
    private final Map<String, MentionStatus> mentionStatuses = new HashMap<>();

    @BeforeEach
    void setUp() {
        attractionRepository = Mockito.mock(AttractionRepository.class);
        korServiceClient = Mockito.mock(KorServiceClient.class);
        cacheService = Mockito.mock(ExternalApiCacheService.class);
        regionCodeRepository = Mockito.mock(RegionCodeRepository.class);
        centerRankService = Mockito.mock(CenterRankService.class);
        signalLookupService = Mockito.mock(SignalLookupService.class);
        visitTimingService = Mockito.mock(VisitTimingService.class);

        attractionService = new AttractionService(korServiceClient, cacheService,
                regionCodeRepository, centerRankService, signalLookupService,
                visitTimingService, attractionRepository);

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

        given(attractionRepository.findLatestImportedAt()).willReturn(CATALOG_IMPORTED_AT);
    }

    // --- 카탈로그 전수 정렬 ---------------------------------------------------

    @Test
    @DisplayName("시·군을 지정하지 않은 언급량 정렬은 카탈로그 전수(4,746곳)를 대상으로 한다")
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
        given(attractionRepository.findLatestImportedAt()).willReturn(null);
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
        given(attractionRepository.findLatestImportedAt()).willReturn(null);
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
        verify(attractionRepository, never()).findLatestImportedAt();
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

    private static AttractionSearchRequest sortRequest(AttractionSort sort, int page, int size) {
        return new AttractionSearchRequest(null, null, page, size, sort, null, null,
                null, null, null, null);
    }
}
