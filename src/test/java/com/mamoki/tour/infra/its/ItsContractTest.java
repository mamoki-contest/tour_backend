package com.mamoki.tour.infra.its;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.mamoki.tour.domain.currentaccess.dto.RoadFlowView;
import com.mamoki.tour.domain.currentaccess.dto.RoadFlowView.RoadSegmentView;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.its.dto.ItsItem;
import com.mamoki.tour.infra.its.dto.ItsResponse;

/**
 * 저장한 실제 응답으로 국가교통정보센터 교통소통정보 계약을 검증한다.
 * 외부 API 를 호출하지 않으므로 매 테스트마다 안전하게 돌릴 수 있다.
 *
 * <p>fixture 는 경포해변 좌표로 실제 호출해 받은 44개 구간이다. 공공데이터포털 계열과 달리
 * header/body 가 최상위에 바로 오고 성공 코드가 문자열이 아니라 숫자 0 이다.
 */
class ItsContractTest {

    private ItsClient client;
    private ItsResponse response;

    @BeforeEach
    void setUp() throws Exception {
        client = new ItsClient(properties("https://openapi.its.go.kr:9443", "test-key"), RestClient.builder());
        response = client.parse(fixture());
    }

    // --- 응답 계약 -------------------------------------------------------------

    @Test
    @DisplayName("실제 응답을 구간 목록으로 해석한다")
    void parsesRealResponse() {
        assertThat(response.totalCount()).isEqualTo(44);
        assertThat(response.items()).hasSize(44);

        ItsItem first = response.items().get(0);
        assertThat(first.roadName()).isEqualTo("경포로");
        assertThat(first.speed()).isEqualTo("40");
        assertThat(first.travelTime()).isEqualTo("10.4");
        assertThat(first.createdDate()).isEqualTo("20260918210500");
        // 링크 좌표가 없다. 그래서 가장 가까운 도로를 고르지 못하고 사각형을 좁게 잡는다.
        assertThat(first.linkId()).isNotBlank();
    }

    @Test
    @DisplayName("성공 코드는 문자열이 아니라 숫자 0 이다")
    void successCodeIsNumericZero() {
        assertThat(response.header().resultCode()).isZero();
        assertThat(response.header().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("공급자가 오류 코드를 주면 외부 호출 실패로 바꾼다")
    void rejectsErrorCode() {
        String body = """
                {"header":{"resultCode":1,"resultMsg":"INVALID_KEY"},"body":{"totalCount":0,"items":[]}}
                """;

        assertThatThrownBy(() -> client.parse(body))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("오류를 반환했습니다");
    }

    @Test
    @DisplayName("해석할 수 없는 본문은 외부 호출 실패로 바꾼다")
    void rejectsMalformedBody() {
        assertThatThrownBy(() -> client.parse("<html>error</html>"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("해석하지 못했습니다");
    }

    @Test
    @DisplayName("header 가 없는 응답도 실패로 다룬다")
    void rejectsMissingHeader() {
        assertThatThrownBy(() -> client.parse("""
                {"body":{"totalCount":0,"items":[]}}"""))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("구간이 없는 정상 응답은 빈 목록이다")
    void emptyBodyIsEmptyList() {
        ItsResponse empty = client.parse("""
                {"header":{"resultCode":0,"resultMsg":"SUCCESS"},"body":{"totalCount":0}}""");

        assertThat(empty.items()).isEmpty();
        assertThat(empty.totalCount()).isZero();
    }

    @Test
    @DisplayName("인증키가 없으면 호출하지 않고 실패로 알린다")
    void refusesToCallWithoutApiKey() {
        ItsClient keyless = new ItsClient(properties("https://openapi.its.go.kr:9443", ""), RestClient.builder());

        assertThatThrownBy(() -> keyless.trafficInfoJson(
                new BigDecimal("37.8050"), new BigDecimal("128.9060")))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("인증키");
    }

    // --- 캐시 키 ---------------------------------------------------------------

    @Test
    @DisplayName("같은 좌표는 항상 같은 캐시 키를 만든다")
    void cacheKeyIsStable() {
        String first = client.trafficInfoKey(new BigDecimal("37.8050"), new BigDecimal("128.9060"));
        String second = client.trafficInfoKey(new BigDecimal("37.8050"), new BigDecimal("128.9060"));

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("trafficInfo?");
    }

    @Test
    @DisplayName("캐시 키는 관광지 좌표를 중심으로 한 사각형을 담는다")
    void cacheKeyCarriesTheBoundingBox() {
        String key = client.trafficInfoKey(new BigDecimal("37.8050"), new BigDecimal("128.9060"));

        // half-span 0.005 도를 사방으로 더한 사각형이다.
        assertThat(key).contains("minX=128.9010", "maxX=128.9110");
        assertThat(key).contains("minY=37.8000", "maxY=37.8100");
        assertThat(key).contains("getType=json");
        // 인증키는 캐시 키에 넣지 않는다.
        assertThat(key).doesNotContain("apiKey");
    }

    @Test
    @DisplayName("좌표가 다르면 캐시 키도 다르다")
    void cacheKeyChangesWithCoordinates() {
        String gyeongpo = client.trafficInfoKey(new BigDecimal("37.8050"), new BigDecimal("128.9060"));
        String jeongdongjin = client.trafficInfoKey(new BigDecimal("37.6912"), new BigDecimal("129.0334"));

        assertThat(gyeongpo).isNotEqualTo(jeongdongjin);
    }

    // --- 도로명별 요약 ---------------------------------------------------------

    @Test
    @DisplayName("44개 구간을 도로명별 요약으로 묶는다")
    void summarizesRealResponseByRoad() {
        RoadFlowView view = ItsItemConverter.convert(response.items());

        assertThat(view.status()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(view.linkCount()).isEqualTo(44);
        assertThat(view.averageSpeed()).isCloseTo(28.6, within(0.05));
        assertThat(view.observedAt()).isEqualTo(LocalDateTime.of(2026, 9, 18, 21, 5, 0));
    }

    @Test
    @DisplayName("도로는 관측 구간이 많은 순으로 다섯 개까지만 담는다")
    void keepsTopRoadsOnly() {
        RoadFlowView view = ItsItemConverter.convert(response.items());

        // fixture 에는 이름 있는 도로가 일곱 가지 있다(이름 없음 표기 '-' 는 도로가 아니다).
        assertThat(view.roads()).hasSize(5);
        assertThat(view.roads()).extracting(RoadSegmentView::linkCount)
                .containsExactly(12, 12, 6, 6, 2);
        assertThat(view.roads().get(0).roadName()).isEqualTo("경포로");
        assertThat(view.roads().get(0).averageSpeed()).isCloseTo(32.9, within(0.05));
        assertThat(view.roads().get(0).averageTravelTime()).isCloseTo(38.9, within(0.05));
        // 구간 수가 같으면 도로명 순으로 가른다.
        assertThat(view.roads().get(1).roadName()).isEqualTo("해안로");
        // 구간 2개짜리 도로 셋 중 도로명이 가장 앞서는 것이 다섯 번째 자리를 차지한다.
        assertThat(view.roads().get(4).roadName()).isEqualTo("경포로463번안길");
    }

    @Test
    @DisplayName("속도를 읽을 수 없는 응답은 값을 지어내지 않고 정보 없음이다")
    void unreadableSpeedsAreNoData() {
        RoadFlowView view = ItsItemConverter.convert(List.of(
                item("경포로", "", "10.4"),
                item("해안로", "알수없음", "12.0")));

        assertThat(view.status()).isEqualTo(DataStatus.NO_DATA);
        assertThat(view.linkCount()).isNull();
        assertThat(view.averageSpeed()).isNull();
        assertThat(view.roads()).isEmpty();
        assertThat(view.observedAt()).isNull();
    }

    @Test
    @DisplayName("구간이 하나도 없으면 정보 없음이다")
    void emptyItemsAreNoData() {
        assertThat(ItsItemConverter.convert(List.of()).status()).isEqualTo(DataStatus.NO_DATA);
    }

    @Test
    @DisplayName("도로명이 빈 구간은 평균에는 넣고 도로 목록에서는 뺀다")
    void namelessSegmentsCountTowardAverageOnly() {
        RoadFlowView view = ItsItemConverter.convert(List.of(
                item("경포로", "40", "10.0"),
                item("", "20", "10.0"),
                item(null, "30", "10.0")));

        assertThat(view.linkCount()).isEqualTo(3);
        assertThat(view.averageSpeed()).isCloseTo(30.0, within(0.05));
        assertThat(view.roads()).extracting(RoadSegmentView::roadName).containsExactly("경포로");
    }

    @Test
    @DisplayName("관측 생성시각이 섞여 있으면 가장 최근 것을 기준으로 삼는다")
    void observedAtIsTheLatest() {
        RoadFlowView view = ItsItemConverter.convert(List.of(
                new ItsItem("경포로", "", "", "1", "", "", "40", "10.0", "20260918210500"),
                new ItsItem("경포로", "", "", "2", "", "", "40", "10.0", "20260918211000"),
                new ItsItem("경포로", "", "", "3", "", "", "40", "10.0", "잘못된값")));

        assertThat(view.observedAt()).isEqualTo(LocalDateTime.of(2026, 9, 18, 21, 10, 0));
    }

    @Test
    @DisplayName("통행시간이 빠진 구간이 있어도 남은 값으로 평균을 낸다")
    void averagesTravelTimeOverPresentValues() {
        RoadFlowView view = ItsItemConverter.convert(List.of(
                item("경포로", "40", "10.0"),
                item("경포로", "40", ""),
                item("경포로", "40", "20.0")));

        assertThat(view.roads().get(0).averageTravelTime()).isCloseTo(15.0, within(0.05));
    }

    /**
     * 공급자는 이름이 없는 구간의 도로명을 빈 문자열이 아니라 {@code "-"} 로 내려준다(#64).
     * 이름이 아니라 이름 없음의 표기이므로 빈 문자열과 똑같이 다룬다.
     */
    @Test
    @DisplayName("이름 자리에 '-' 만 온 구간은 도로 목록에 담지 않는다")
    void dashIsNotARoadName() {
        RoadFlowView view = ItsItemConverter.convert(response.items());

        assertThat(view.roads()).extracting(RoadSegmentView::roadName).doesNotContain("-");
        // 목록에서만 빠진다. 관측 구간 수 44 에는 '-' 구간 2개가 그대로 들어 있다.
        assertThat(view.linkCount()).isEqualTo(44);
    }

    @Test
    @DisplayName("글자도 숫자도 없는 이름은 이름 없음으로 다룬다")
    void punctuationOnlyNamesAreNameless() {
        RoadFlowView view = ItsItemConverter.convert(List.of(
                item("경포로", "40", "10.0"),
                item("-", "20", "10.0"),
                item(" - ", "20", "10.0"),
                item("--", "20", "10.0"),
                item("—", "20", "10.0"),
                item(".", "20", "10.0")));

        assertThat(view.linkCount()).isEqualTo(6);
        assertThat(view.roads()).extracting(RoadSegmentView::roadName).containsExactly("경포로");
    }

    @Test
    @DisplayName("글자나 숫자가 하나라도 있으면 도로명으로 살린다")
    void namesWithLettersOrDigitsSurvive() {
        RoadFlowView view = ItsItemConverter.convert(List.of(
                item("경포로463번길", "40", "10.0"),
                item("7번국도", "40", "10.0"),
                item("국도-7", "40", "10.0")));

        assertThat(view.roads()).extracting(RoadSegmentView::roadName)
                .containsExactlyInAnyOrder("경포로463번길", "7번국도", "국도-7");
    }

    // --- 도우미 ---------------------------------------------------------------

    private static ItsItem item(String roadName, String speed, String travelTime) {
        return new ItsItem(roadName, "", "", "링크", "", "", speed, travelTime, "20260918210500");
    }

    private static ItsProperties properties(String baseUrl, String apiKey) {
        return new ItsProperties(baseUrl, apiKey, new BigDecimal("0.005"),
                Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    private static String fixture() throws Exception {
        try (InputStream in = ItsContractTest.class
                .getResourceAsStream("/fixtures/its-trafficInfo.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
