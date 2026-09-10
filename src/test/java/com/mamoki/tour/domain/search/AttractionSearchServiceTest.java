package com.mamoki.tour.domain.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.attraction.dto.OnlineMentionView;
import com.mamoki.tour.domain.attraction.dto.TmapRankView;
import com.mamoki.tour.domain.attraction.service.AttractionService;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.search.dto.AttractionSearchResponse;
import com.mamoki.tour.domain.search.enums.SearchResultType;
import com.mamoki.tour.domain.search.enums.SupportedTheme;
import com.mamoki.tour.domain.search.service.AttractionSearchService;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.infra.korservice.KorServiceClient;
import com.mamoki.tour.infra.korservice.KorServiceProperties;

/**
 * 테마 검색과 일반 검색의 갈림, 그리고 0건 처리.
 *
 * <p>fixture 는 강원 `해수욕장` 검색 10건과, 결과가 없을 때 공급자가 내려주는 응답이다.
 * 결과가 없으면 items 가 객체가 아니라 빈 문자열로 온다.
 */
class AttractionSearchServiceTest {

    private String beachFixture;
    private String emptyFixture;
    private ExternalApiCacheService cacheService;
    private AttractionSearchService service;

    @BeforeEach
    void setUp() throws Exception {
        beachFixture = read("/fixtures/korservice-searchKeyword2.json");
        emptyFixture = read("/fixtures/korservice-searchKeyword2-empty.json");

        KorServiceClient client = Mockito.mock(KorServiceClient.class);
        given(client.searchKeywordKey(anyString(), any(), any(), anyString(), anyInt(), anyInt()))
                .willAnswer(invocation -> "searchKeyword2?keyword=" + invocation.getArgument(3));
        given(client.parse(anyString(), anyString()))
                .willAnswer(invocation -> new KorServiceClient(properties())
                        .parse(invocation.getArgument(0), invocation.getArgument(1)));

        cacheService = Mockito.mock(ExternalApiCacheService.class);
        stubKeyword("해수욕장", CachedResponse.available(beachFixture, LocalDateTime.now()));

        AttractionService attractionService = Mockito.mock(AttractionService.class);
        given(attractionService.describe(any(), any(), any(), any()))
                .willAnswer(invocation -> {
                    List<AttractionSnapshot> snapshots = invocation.getArgument(0);
                    return snapshots.stream()
                            .map(snapshot -> AttractionResponse.of(snapshot, null, null,
                                    OnlineMentionView.notCollected(null),
                                    TmapRankView.notAvailable(), null))
                            .toList();
                });

        service = new AttractionSearchService(client, cacheService, attractionService);
    }

    private static String read(String path) throws Exception {
        try (InputStream in = AttractionSearchServiceTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private KorServiceProperties properties() {
        return new KorServiceProperties("http://example.invalid", "key", "tour",
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    /** 지정한 검색어에만 내용을 주고 나머지는 0건 응답을 돌려준다. */
    private void stubKeyword(String keyword, CachedResponse response) {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willAnswer(invocation -> {
                    String requestKey = invocation.getArgument(1);

                    return requestKey.endsWith("keyword=" + keyword)
                            ? response
                            : CachedResponse.available(emptyFixture, LocalDateTime.now());
                });
    }

    @Test
    @DisplayName("지원 테마는 SUPPORTED_THEME 으로 표시된다")
    void marksSupportedTheme() {
        AttractionSearchResponse response = service.search("해수욕장", null, 1, 20);

        assertThat(response.resultType()).isEqualTo(SearchResultType.SUPPORTED_THEME);
        assertThat(response.appliedTheme().code()).isEqualTo(SupportedTheme.BEACH);
        assertThat(response.appliedTheme().name()).isEqualTo("해수욕장");
        assertThat(response.items()).isNotEmpty();
    }

    @Test
    @DisplayName("동의어도 같은 지원 테마 결과로 이어진다")
    void resolvesSynonymToSameTheme() {
        AttractionSearchResponse bySynonym = service.search("바닷가", null, 1, 20);

        assertThat(bySynonym.resultType()).isEqualTo(SearchResultType.SUPPORTED_THEME);
        assertThat(bySynonym.appliedTheme().code()).isEqualTo(SupportedTheme.BEACH);
    }

    @Test
    @DisplayName("미지원 검색어는 GENERAL_SEARCH 로 표시되고 테마가 붙지 않는다")
    void marksGeneralSearch() {
        AttractionSearchResponse response = service.search("강릉 맛집", null, 1, 20);

        assertThat(response.resultType()).isEqualTo(SearchResultType.GENERAL_SEARCH);
        assertThat(response.appliedTheme()).isNull();
        assertThat(response.appliedQuery()).isEqualTo("강릉맛집");
    }

    @Test
    @DisplayName("이름이 테마와 맞지 않는 장소는 지원 테마 결과에 담기지 않는다")
    void appliesEligibilityToThemeResults() {
        AttractionSearchResponse response = service.search("해수욕장", null, 1, 20);

        assertThat(response.items()).isNotEmpty();
        assertThat(response.items()).allSatisfy(item ->
                assertThat(item.name().replaceAll("\\s+", ""))
                        .containsAnyOf("해수욕장", "해변"));
    }

    @Test
    @DisplayName("일반 검색은 자격을 적용하지 않고 공급자 결과를 그대로 쓴다")
    void generalSearchKeepsProviderResults() {
        stubKeyword("강릉맛집", CachedResponse.available(beachFixture, LocalDateTime.now()));

        AttractionSearchResponse response = service.search("강릉 맛집", null, 1, 20);

        assertThat(response.resultType()).isEqualTo(SearchResultType.GENERAL_SEARCH);
        assertThat(response.totalCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("0건은 정보 없음이 아니라 빈 결과로 구분된다")
    void distinguishesEmptyResultFromNoData() {
        AttractionSearchResponse response = service.search("억새", null, 1, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isZero();
        assertThat(response.dataStatus()).isEqualTo(DataStatus.AVAILABLE);
    }

    @Test
    @DisplayName("공급자를 못 부르면 정보 없음으로 알린다")
    void reportsNoDataWhenProviderUnavailable() {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.noData());

        AttractionSearchResponse response = service.search("해수욕장", null, 1, 20);

        assertThat(response.dataStatus()).isEqualTo(DataStatus.NO_DATA);
        assertThat(response.collectedAt()).isNull();
        assertThat(response.items()).isEmpty();
    }

    @Test
    @DisplayName("결과가 없으면 가까운 지원 테마를 제안한다")
    void suggestsNearbyThemeWhenEmpty() {
        AttractionSearchResponse response = service.search("계고", null, 1, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.suggestedThemes()).isNotEmpty();
    }

    @Test
    @DisplayName("결과가 있으면 다른 테마를 권하지 않는다")
    void doesNotSuggestWhenResultsExist() {
        AttractionSearchResponse response = service.search("해수욕장", null, 1, 20);

        assertThat(response.items()).isNotEmpty();
        assertThat(response.suggestedThemes()).isEmpty();
    }

    @Test
    @DisplayName("페이지를 나눠도 전체 건수는 조건에 맞는 전체를 가리킨다")
    void pagesWithoutLosingTotalCount() {
        AttractionSearchResponse firstPage = service.search("해수욕장", null, 1, 3);

        assertThat(firstPage.items()).hasSize(3);
        assertThat(firstPage.totalCount()).isGreaterThan(3);
        assertThat(firstPage.page()).isEqualTo(1);
        assertThat(firstPage.size()).isEqualTo(3);
    }
}
