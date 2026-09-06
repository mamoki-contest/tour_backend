package com.mamoki.tour.domain.attraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.attraction.service.CenterRankService;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.infra.locgohub.LocgoHubClient;

class CenterRankServiceTest {

    private static final String GANGNEUNG = "51150";

    private CenterRankService centerRankService;
    private ExternalApiCacheService cacheService;

    @BeforeEach
    void setUp() throws Exception {
        String fixture;
        try (InputStream in = getClass().getResourceAsStream(
                "/fixtures/locgohub-areaBasedList1.json")) {
            fixture = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        LocgoHubClient client = Mockito.mock(LocgoHubClient.class);
        given(client.areaBasedListKey(anyString(), anyInt())).willReturn("areaBasedList1?signguCd=51150");
        given(client.parse(anyString()))
                .willAnswer(invocation -> new LocgoHubClient(properties()).parse(invocation.getArgument(0)));

        cacheService = Mockito.mock(ExternalApiCacheService.class);
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available(fixture, LocalDateTime.now()));

        centerRankService = new CenterRankService(client, cacheService);
    }

    private com.mamoki.tour.infra.locgohub.LocgoHubProperties properties() {
        return new com.mamoki.tour.infra.locgohub.LocgoHubProperties(
                "http://example.invalid", "key", "tour", "202507",
                java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("이름이 정확히 일치하면 시·군 내부 순위를 붙인다")
    void matchesByExactName() {
        Map<String, Integer> ranks = centerRankService.resolveRanks(GANGNEUNG, List.of(
                attraction("126508", "경포해변", "37.8055042534", "128.9078622659")));

        assertThat(ranks).containsEntry("126508", 2);
    }

    @Test
    @DisplayName("표기가 달라도 정규화하면 매칭된다")
    void matchesAcrossNotationDifference() {
        Map<String, Integer> ranks = centerRankService.resolveRanks(GANGNEUNG, List.of(
                attraction("999", "아르떼뮤지엄 강릉", "37.7917558331", "128.9074180316")));

        assertThat(ranks).containsEntry("999", 5);
    }

    @Test
    @DisplayName("이름이 같아도 좌표가 멀면 다른 장소로 보고 순위를 붙이지 않는다")
    void rejectsDistantSameName() {
        Map<String, Integer> ranks = centerRankService.resolveRanks(GANGNEUNG, List.of(
                attraction("888", "경포해변", "37.5665000000", "126.9780000000")));

        assertThat(ranks).doesNotContainKey("888");
    }

    @Test
    @DisplayName("좌표가 없으면 이름 일치만으로 인정한다")
    void allowsMatchWithoutCoordinates() {
        Map<String, Integer> ranks = centerRankService.resolveRanks(GANGNEUNG, List.of(
                attraction("777", "강릉중앙시장", null, null)));

        assertThat(ranks).containsEntry("777", 1);
    }

    @Test
    @DisplayName("중심관광지에 없는 장소는 순위를 만들어내지 않는다")
    void leavesUnmatchedOut() {
        Map<String, Integer> ranks = centerRankService.resolveRanks(GANGNEUNG, List.of(
                attraction("111", "이름 없는 어느 관광지", "37.75", "128.89")));

        assertThat(ranks).isEmpty();
    }

    @Test
    @DisplayName("공급자 데이터가 없으면 빈 결과를 돌려주고 예외를 던지지 않는다")
    void returnsEmptyWhenProviderHasNoData() {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.noData());

        Map<String, Integer> ranks = centerRankService.resolveRanks(GANGNEUNG, List.of(
                attraction("126508", "경포해변", "37.8055042534", "128.9078622659")));

        assertThat(ranks).isEmpty();
    }

    private AttractionSnapshot attraction(String contentId, String name, String lat, String lon) {
        return new AttractionSnapshot(contentId, name, null, null,
                lat == null ? null : new BigDecimal(lat),
                lon == null ? null : new BigDecimal(lon),
                "12", GANGNEUNG, LocalDateTime.now(), "KorService2");
    }
}
