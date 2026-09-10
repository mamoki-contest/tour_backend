package com.mamoki.tour.domain.region;

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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.region.dto.RegionVisitScale;
import com.mamoki.tour.domain.region.dto.RegionVisitScaleResponse;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.region.service.RegionVisitScaleService;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.infra.datalab.DataLabClient;
import com.mamoki.tour.infra.datalab.DataLabProperties;

/**
 * 지역 방문 규모 조립 검증.
 *
 * <p>fixture 는 강원 18개 시·군과 강원 밖 2개 시·군구가 함께 든 실제 응답이다.
 */
class RegionVisitScaleServiceTest {

    private String fixture;
    private ExternalApiCacheService cacheService;
    private RegionCodeRepository regionCodeRepository;
    private RegionVisitScaleService service;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/fixtures/datalab-locgoRegnVisitrDDList.json")) {
            fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        DataLabClient client = Mockito.mock(DataLabClient.class);
        given(client.regionVisitorsKey(any(), any(), anyInt()))
                .willAnswer(invocation -> "locgoRegnVisitrDDList?pageNo=" + invocation.getArgument(2));
        given(client.parse(anyString()))
                .willAnswer(invocation -> new DataLabClient(properties()).parse(invocation.getArgument(0)));

        cacheService = Mockito.mock(ExternalApiCacheService.class);
        stubFirstPage(CachedResponse.available(fixture, LocalDateTime.now()));

        regionCodeRepository = Mockito.mock(RegionCodeRepository.class);
        given(regionCodeRepository.findAllByAreaCode("32")).willReturn(gangwonRegions());

        service = new RegionVisitScaleService(client, properties(), cacheService, regionCodeRepository);
    }

    /**
     * 첫 페이지만 내용을 주고 다음 페이지는 빈 항목으로 돌려준다.
     *
     * <p>공급자는 마지막 페이지 뒤로 빈 목록을 준다. 모든 페이지에 같은 응답을 물리면
     * 같은 시·군이 여러 번 더해져 실제와 다른 합계로 검증하게 된다.
     */
    private void stubFirstPage(CachedResponse firstPage) {
        String empty = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":{"item":[]},"numOfRows":1000,"pageNo":2,"totalCount":807}}}""";

        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willAnswer(invocation -> {
                    String requestKey = invocation.getArgument(1);

                    return requestKey.endsWith("pageNo=1")
                            ? firstPage
                            : CachedResponse.available(empty, LocalDateTime.now());
                });
    }

    private DataLabProperties properties() {
        return new DataLabProperties("http://example.invalid", "key", "tour", "20260810",
                30, 1, 1000, 10, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    /** fixture 에 든 강원 18개 시·군. sigunguCode 는 아직 확인되지 않아 비어 있다. */
    private static List<RegionCode> gangwonRegions() {
        return List.of("51110", "51130", "51150", "51170", "51190", "51210", "51230", "51720",
                        "51730", "51750", "51760", "51770", "51780", "51790", "51800", "51810",
                        "51820", "51830").stream()
                .map(lawdCode -> RegionCode.builder()
                        .lawdCode(lawdCode)
                        .areaCode("32")
                        .name(lawdCode + "시·군")
                        .build())
                .toList();
    }

    private Map<String, RegionVisitScale> byLawdCode(RegionVisitScaleResponse response) {
        return response.items().stream()
                .collect(Collectors.toMap(RegionVisitScale::lawdCode, Function.identity()));
    }

    @Test
    @DisplayName("강원 18개 시·군이 같은 기준 기간과 출처로 반환된다")
    void returnsGangwonRegionsWithPeriodAndSource() {
        RegionVisitScaleResponse response = service.getVisitScale();

        assertThat(response.items()).hasSize(18);
        assertThat(response.totalRegions()).isEqualTo(18);
        assertThat(response.availableRegions()).isEqualTo(18);
        assertThat(response.dataStatus()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(response.collectedAt()).isNotNull();
        assertThat(response.source()).isNotBlank();
        assertThat(response.periodEnd()).isEqualTo(java.time.LocalDate.of(2026, 8, 10));
        assertThat(response.periodStart()).isEqualTo(java.time.LocalDate.of(2026, 8, 10));
    }

    @Test
    @DisplayName("강원 밖 시·군구는 담지 않는다")
    void excludesRegionsOutsideGangwon() {
        RegionVisitScaleResponse response = service.getVisitScale();

        assertThat(byLawdCode(response)).doesNotContainKeys("11110", "26110");
        assertThat(response.items()).allSatisfy(
                item -> assertThat(item.lawdCode()).startsWith("51"));
    }

    @Test
    @DisplayName("모든 시·군이 상대 구간과 순위를 받는다")
    void assignsLevelAndRank() {
        RegionVisitScaleResponse response = service.getVisitScale();

        assertThat(response.items()).allSatisfy(item -> {
            assertThat(item.level()).isNotNull();
            assertThat(item.rank()).isNotNull();
            assertThat(item.visitorCount()).isNotNull();
            assertThat(item.dataStatus()).isEqualTo(DataStatus.AVAILABLE);
        });
    }

    @Test
    @DisplayName("공급자 응답에 없는 시·군은 최하 구간이 아니라 정보 없음으로 남는다")
    void marksMissingRegionAsNoData() {
        List<RegionCode> withExtra = new java.util.ArrayList<>(gangwonRegions());
        withExtra.add(RegionCode.builder().lawdCode("51999").areaCode("32").name("없는시").build());
        given(regionCodeRepository.findAllByAreaCode("32")).willReturn(withExtra);

        RegionVisitScaleResponse response = service.getVisitScale();
        RegionVisitScale missing = byLawdCode(response).get("51999");

        assertThat(missing.dataStatus()).isEqualTo(DataStatus.NO_DATA);
        assertThat(missing.visitorCount()).isNull();
        assertThat(missing.level()).isNull();
        assertThat(missing.rank()).isNull();
        assertThat(response.totalRegions()).isEqualTo(19);
        assertThat(response.availableRegions()).isEqualTo(18);
    }

    @Test
    @DisplayName("공급자를 못 부르면 시·군 목록은 남기고 전체를 정보 없음으로 알린다")
    void reportsNoDataWhenProviderUnavailable() {
        stubFirstPage(CachedResponse.noData());

        RegionVisitScaleResponse response = service.getVisitScale();

        assertThat(response.dataStatus()).isEqualTo(DataStatus.NO_DATA);
        assertThat(response.collectedAt()).isNull();
        assertThat(response.availableRegions()).isZero();
        assertThat(response.items()).hasSize(18);
        assertThat(response.items()).allSatisfy(
                item -> assertThat(item.dataStatus()).isEqualTo(DataStatus.NO_DATA));
    }

    @Test
    @DisplayName("최종 정상 데이터로 응답하면 STALE 을 그대로 전달한다")
    void propagatesStaleStatus() {
        stubFirstPage(CachedResponse.stale(fixture, LocalDateTime.now().minusDays(2)));

        RegionVisitScaleResponse response = service.getVisitScale();

        assertThat(response.dataStatus()).isEqualTo(DataStatus.STALE);
        assertThat(response.collectedAt()).isNotNull();
    }
}
