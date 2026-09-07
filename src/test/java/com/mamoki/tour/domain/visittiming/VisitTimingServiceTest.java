package com.mamoki.tour.domain.visittiming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;
import com.mamoki.tour.domain.visittiming.enums.DateMode;
import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;
import com.mamoki.tour.domain.visittiming.service.VisitTimingService;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.infra.tatscnctrrate.TatsCnctrRateClient;
import com.mamoki.tour.infra.tatscnctrrate.TatsCnctrRateProperties;

/**
 * 목록에 날짜 탐색 결과를 이어 붙이는 서비스.
 *
 * <p>여기서 지키려는 것은 두 가지다. 하나는 호출 수 — 조회 단위가 시·군이므로 관광지마다
 * 부르지 않고 시·군마다 한 번만 불러야 한다. 다른 하나는 결측 처리 — 매칭에 실패하면
 * 값을 만들어내지 않고 정보 없음으로 남겨야 한다.
 */
class VisitTimingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);
    private static final String GANGNEUNG = "51150";
    private static final String SOKCHO = "51210";

    private VisitTimingService visitTimingService;
    private ExternalApiCacheService cacheService;
    private TatsCnctrRateClient client;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(TatsCnctrRateClient.class);
        given(client.tatsCnctrRatedListKey(anyString(), anyInt()))
                .willAnswer(call -> "tatsCnctrRatedList?signguCd="
                        + call.getArgument(0) + "&pageNo=" + call.getArgument(1));
        given(client.parse(anyString()))
                .willAnswer(call -> realClient().parse(call.getArgument(0)));
        given(client.rowsPerPage()).willReturn(1_000);
        given(client.maxPages()).willReturn(5);

        cacheService = Mockito.mock(ExternalApiCacheService.class);
        visitTimingService = new VisitTimingService(client, cacheService);
    }

    private TatsCnctrRateClient realClient() {
        return new TatsCnctrRateClient(new TatsCnctrRateProperties(
                "http://example.invalid", "key", "tour", 1_000, 5,
                Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("같은 시·군의 관광지가 여러 곳이어도 공급자는 한 번만 부른다")
    void callsProviderOncePerRegion() {
        givenResponse(body(30, GANGNEUNG, "경포대", "경포해수욕장", "정동진"));

        Map<String, VisitTiming> result = visitTimingService.resolve(
                List.of(attraction("1", "경포대", GANGNEUNG),
                        attraction("2", "경포해수욕장", GANGNEUNG),
                        attraction("3", "정동진", GANGNEUNG)),
                DateMode.FLEXIBLE, null, TODAY);

        assertThat(result).hasSize(3);
        verify(cacheService, times(1)).fetch(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("시·군이 다르면 시·군 수만큼 부른다")
    void callsProviderPerDistinctRegion() {
        givenResponse(body(30, GANGNEUNG, "경포대"));

        visitTimingService.resolve(
                List.of(attraction("1", "경포대", GANGNEUNG),
                        attraction("2", "경포대", SOKCHO)),
                DateMode.FLEXIBLE, null, TODAY);

        verify(cacheService, times(2)).fetch(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("날짜 모드를 지정하지 않으면 예측을 조회하지 않는다")
    void skipsWhenDateModeAbsent() {
        Map<String, VisitTiming> result = visitTimingService.resolve(
                List.of(attraction("1", "경포대", GANGNEUNG)), null, null, TODAY);

        assertThat(result).isEmpty();
        verify(cacheService, never()).fetch(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("법정동 코드 형식이 어긋나면 호출하지 않고 정보 없음으로 남긴다")
    void skipsMalformedLawdCode() {
        Map<String, VisitTiming> result = visitTimingService.resolve(
                List.of(attraction("1", "경포대", "51"),
                        attraction("2", "정동진", null)),
                DateMode.FLEXIBLE, null, TODAY);

        assertThat(result.get("1").status()).isEqualTo(VisitTimingStatus.NO_DATA);
        assertThat(result.get("2").status()).isEqualTo(VisitTimingStatus.NO_DATA);
        verify(cacheService, never()).fetch(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("표기가 달라도 정규화한 이름으로 예측을 이어 붙인다")
    void matchesByNormalizedName() {
        givenResponse(body(30, GANGNEUNG, "강릉 경포대"));

        Map<String, VisitTiming> result = visitTimingService.resolve(
                List.of(attraction("1", "강릉경포대", GANGNEUNG)),
                DateMode.FLEXIBLE, null, TODAY);

        assertThat(result.get("1").status()).isEqualTo(VisitTimingStatus.LOW);
        assertThat(result.get("1").forecastDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("공급자 목록에 없는 장소는 값을 만들어내지 않는다")
    void leavesUnmatchedAttractionsWithoutData() {
        givenResponse(body(30, GANGNEUNG, "경포대"));

        Map<String, VisitTiming> result = visitTimingService.resolve(
                List.of(attraction("1", "경포대", GANGNEUNG),
                        attraction("2", "공급자에없는장소", GANGNEUNG)),
                DateMode.FLEXIBLE, null, TODAY);

        assertThat(result.get("1").status()).isEqualTo(VisitTimingStatus.LOW);
        assertThat(result.get("2").status()).isEqualTo(VisitTimingStatus.NO_DATA);
        assertThat(result.get("2").quietestDate()).isNull();
        assertThat(result.get("2").forecastDays()).isZero();
    }

    @Test
    @DisplayName("공급자 데이터를 못 받으면 목록은 유지하고 정보 없음으로 알린다")
    void reportsNoDataWhenProviderUnavailable() {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.noData());

        Map<String, VisitTiming> result = visitTimingService.resolve(
                List.of(attraction("1", "경포대", GANGNEUNG)),
                DateMode.FLEXIBLE, null, TODAY);

        assertThat(result.get("1").status()).isEqualTo(VisitTimingStatus.NO_DATA);
        assertThat(result.get("1").dataStatus()).isEqualTo(DataStatus.NO_DATA);
        assertThat(result.get("1").collectedAt()).isNull();
    }

    @Test
    @DisplayName("최종 정상 데이터로 응답하면 상태와 기준 시점을 함께 알린다")
    void surfacesStaleState() {
        LocalDateTime collectedAt = LocalDateTime.of(2026, 9, 6, 12, 0);
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.stale(body(30, GANGNEUNG, "경포대"), collectedAt));

        Map<String, VisitTiming> result = visitTimingService.resolve(
                List.of(attraction("1", "경포대", GANGNEUNG)),
                DateMode.FLEXIBLE, null, TODAY);

        assertThat(result.get("1").dataStatus()).isEqualTo(DataStatus.STALE);
        assertThat(result.get("1").collectedAt()).isEqualTo(collectedAt);
        assertThat(result.get("1").source()).isEqualTo("TatsCnctrRateService");
    }

    @Test
    @DisplayName("한 시·군의 행이 한 페이지를 넘으면 남은 페이지를 이어 받는다")
    void followsPagination() {
        // totalCount 2500, 페이지당 1000 → 3페이지.
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available(
                        body(30, 2_500, GANGNEUNG, "경포대"), LocalDateTime.now()));

        visitTimingService.resolve(
                List.of(attraction("1", "경포대", GANGNEUNG)),
                DateMode.FLEXIBLE, null, TODAY);

        verify(cacheService, times(3)).fetch(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("행이 예상보다 많아도 시·군당 페이지 수 상한을 넘지 않는다")
    void capsPagination() {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available(
                        body(30, 999_999, GANGNEUNG, "경포대"), LocalDateTime.now()));

        visitTimingService.resolve(
                List.of(attraction("1", "경포대", GANGNEUNG)),
                DateMode.FLEXIBLE, null, TODAY);

        verify(cacheService, times(5)).fetch(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("확정 모드는 선택일을, 유연 모드는 한산 예상일을 채운다")
    void fillsDateByMode() {
        givenResponse(body(30, GANGNEUNG, "경포대"));

        VisitTiming fixed = visitTimingService.resolve(
                List.of(attraction("1", "경포대", GANGNEUNG)),
                DateMode.FIXED, TODAY.plusDays(3), TODAY).get("1");

        VisitTiming flexible = visitTimingService.resolve(
                List.of(attraction("1", "경포대", GANGNEUNG)),
                DateMode.FLEXIBLE, null, TODAY).get("1");

        assertThat(fixed.dateMode()).isEqualTo(DateMode.FIXED);
        assertThat(fixed.selectedDate()).isEqualTo(TODAY.plusDays(3));
        assertThat(fixed.quietestDate()).isNull();

        assertThat(flexible.dateMode()).isEqualTo(DateMode.FLEXIBLE);
        assertThat(flexible.selectedDate()).isNull();
        assertThat(flexible.quietestDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("모든 응답이 지원 범위를 함께 알린다")
    void alwaysReportsSupportedWindow() {
        givenResponse(body(30, GANGNEUNG, "경포대"));

        VisitTiming timing = visitTimingService.resolve(
                List.of(attraction("1", "경포대", GANGNEUNG)),
                DateMode.FLEXIBLE, null, TODAY).get("1");

        assertThat(timing.supportedFrom()).isEqualTo(TODAY);
        assertThat(timing.supportedTo()).isEqualTo(TODAY.plusDays(29));
    }

    @Test
    @DisplayName("응답 계약에 집중률 원본값이 들어가지 않는다")
    void neverExposesRawConcentrationRate() {
        // 원본값이 나가면 서로 다른 관광지를 그 값으로 줄 세울 수 있게 된다. PRD 가 금지한 절대 순위다.
        List<String> components = java.util.Arrays.stream(VisitTiming.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(components).doesNotContain("rate", "cnctrRate", "concentrationRate", "days");
    }

    private void givenResponse(String body) {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available(body, LocalDateTime.now()));
    }

    private static AttractionSnapshot attraction(String contentId, String name, String lawdCode) {
        return new AttractionSnapshot(
                contentId, name, null, null, null, null, "12", lawdCode, null, "KorService2");
    }

    private String body(int dayCount, String lawdCode, String... names) {
        return body(dayCount, dayCount * names.length, lawdCode, names);
    }

    /**
     * 공급자 응답을 그대로 흉내 낸 JSON. 한 행이 (관광지 1곳 × 1일) 이다.
     * 집중률은 날짜가 뒤로 갈수록 커지므로 가장 이른 날이 가장 한산하다.
     */
    private String body(int dayCount, int totalCount, String lawdCode, String... names) {
        String items = java.util.Arrays.stream(names)
                .flatMap(name -> IntStream.range(0, dayCount)
                        .mapToObj(i -> """
                                {"baseYmd":"%s","areaCd":"%s","areaNm":"강원특별자치도",\
                                "signguCd":"%s","signguNm":"시군","tAtsNm":"%s","cnctrRate":"%d"}"""
                                .formatted(
                                        TODAY.plusDays(i).format(
                                                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")),
                                        lawdCode.substring(0, 2), lawdCode, name, i + 1)))
                .collect(Collectors.joining(","));

        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},\
                "body":{"items":{"item":[%s]},"numOfRows":1000,"pageNo":1,"totalCount":%d}}}"""
                .formatted(items, totalCount);
    }
}
