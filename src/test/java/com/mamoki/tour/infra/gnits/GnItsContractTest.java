package com.mamoki.tour.infra.gnits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.gnits.dto.GnParkInfoResponse;
import com.mamoki.tour.infra.gnits.dto.GnParkRltmResponse;
import com.mamoki.tour.infra.gnits.dto.RealtimeParkingLot;

/**
 * 저장한 실제 응답으로 강릉시 교통정보 조회서비스 계약을 검증한다.
 * 외부 API 를 호출하지 않으므로 매 테스트마다 안전하게 돌릴 수 있다.
 *
 * <p>fixture 는 2026-09-19 두 오퍼레이션을 {@code numOfRows=100} 으로 실제 호출해 받은
 * 응답이다(인증키 마스킹). 주차장 13곳이며 {@code PLOT000008} 은 결번이다.
 */
class GnItsContractTest {

    private GnItsClient client;
    private GnParkInfoResponse info;
    private GnParkRltmResponse realtime;

    @BeforeEach
    void setUp() throws Exception {
        client = new GnItsClient(properties(Duration.ZERO, 2));
        info = client.parseParkInfo(fixture("gnits-getParkInfo"));
        realtime = client.parseParkRltm(fixture("gnits-getParkRltm"));
    }

    private static String fixture(String name) throws Exception {
        try (InputStream in = GnItsContractTest.class
                .getResourceAsStream("/fixtures/" + name + ".json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static GnItsProperties properties(Duration backoff, int retries) {
        return new GnItsProperties("http://example.invalid", "key", 100, retries, backoff,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private List<RealtimeParkingLot> lots() {
        return GnItsParkConverter.join(info.items(), realtime.items());
    }

    private Map<String, RealtimeParkingLot> byName() {
        return lots().stream().collect(Collectors.toMap(
                RealtimeParkingLot::name, Function.identity()));
    }

    @Test
    @DisplayName("정상 응답의 헤더와 총 건수를 읽는다")
    void readsHeaderAndTotalCount() {
        assertThat(info.header().isSuccess()).isTrue();
        assertThat(info.header().resultCode()).isEqualTo("00");
        assertThat(info.totalCount()).isEqualTo(13);
        assertThat(info.items()).hasSize(13);
        assertThat(realtime.totalCount()).isEqualTo(13);
        assertThat(realtime.items()).hasSize(13);
    }

    @Test
    @DisplayName("두 오퍼레이션을 prkId 로 잇는다. 번호가 연속하지 않아도 된다")
    void joinsByParkingLotId() {
        List<RealtimeParkingLot> lots = lots();

        assertThat(lots).hasSize(13);
        assertThat(lots).extracting(RealtimeParkingLot::prkId)
                .doesNotHaveDuplicates()
                // PLOT000008 은 결번이다. 번호가 이어진다고 가정하면 안 된다.
                .doesNotContain("PLOT000008")
                .contains("PLOT000001", "PLOT000014");
        assertThat(lots).allSatisfy(lot -> assertThat(lot.totalLots()).isNotNull());
    }

    @Test
    @DisplayName("yCrdn 이 위도, xCrdn 이 경도다")
    void readsCoordinatesInProviderOrder() {
        RealtimeParkingLot lot = byName().get("강릉역");

        assertThat(lot.latitude()).isEqualByComparingTo(new BigDecimal("37.7623954"));
        assertThat(lot.longitude()).isEqualByComparingTo(new BigDecimal("128.8976185"));
        // 강원 동해안 범위. 뒤집어 읽으면 여기서 걸린다.
        assertThat(lots()).allSatisfy(entry -> {
            assertThat(entry.latitude()).isBetween(new BigDecimal("37"), new BigDecimal("39"));
            assertThat(entry.longitude()).isBetween(new BigDecimal("128"), new BigDecimal("130"));
        });
    }

    @Test
    @DisplayName("availLots 는 점유 대수다. 잔여면은 전체에서 빼서 구한다")
    void treatsAvailLotsAsOccupied() {
        RealtimeParkingLot lot = byName().get("강릉역");

        assertThat(lot.totalLots()).isEqualTo(410);
        assertThat(lot.occupiedLots()).isEqualTo(98);
        assertThat(lot.availableLots()).isEqualTo(312);
        assertThat(lot.hasRealtime()).isTrue();
    }

    @Test
    @DisplayName("점유가 전체와 같으면 잔여면은 0 이다")
    void computesZeroAvailableWhenFull() {
        RealtimeParkingLot lot = byName().get("강문제1공영주차장");

        assertThat(lot.totalLots()).isEqualTo(170);
        assertThat(lot.occupiedLots()).isEqualTo(170);
        assertThat(lot.availableLots()).isZero();
    }

    @Test
    @DisplayName("점유가 0 이면 잔여면은 전체와 같다. 값 자체는 그대로 둔다")
    void computesFullAvailableWhenEmpty() {
        RealtimeParkingLot lot = byName().get("주문진해안주차타워");

        assertThat(lot.occupiedLots()).isZero();
        assertThat(lot.availableLots()).isEqualTo(230);
    }

    @Test
    @DisplayName("점유가 전체보다 크면 잔여면을 만들지 않는다")
    void refusesToInventAvailableLots() {
        RealtimeParkingLot broken = new RealtimeParkingLot(
                "PLOT000099", "깨진주차장", null, null, null, null, 10, 11,
                null, null, null, null, null, null);

        assertThat(broken.availableLots()).isNull();
        assertThat(broken.hasRealtime()).isFalse();
    }

    @Test
    @DisplayName("운영시각은 가변 길이다. 네 자리로 자르지 않는다")
    void keepsVariableLengthOperatingHours() {
        RealtimeParkingLot lot = byName().get("중앙시장제1공영주차장");

        assertThat(lot.weekOpenTime()).isEqualTo("1000");
        // 공휴일이 한 자리로 오는 곳이다.
        assertThat(lot.holiOpenTime()).isEqualTo("0");
        assertThat(lot.holiEndTime()).isEqualTo("0");
    }

    @Test
    @DisplayName("실시간이 없는 주차장은 0 이 아니라 null 로 남는다")
    void keepsMissingCountsNull() {
        List<RealtimeParkingLot> lots =
                GnItsParkConverter.join(info.items(), List.of());

        assertThat(lots).hasSize(13);
        assertThat(lots).allSatisfy(lot -> {
            assertThat(lot.totalLots()).isNull();
            assertThat(lot.occupiedLots()).isNull();
            assertThat(lot.availableLots()).isNull();
            assertThat(lot.hasRealtime()).isFalse();
        });
    }

    @Test
    @DisplayName("빈 응답 {} 은 오류가 아니라 스로틀이다")
    void detectsSilentThrottle() {
        assertThat(client.isThrottled("{}")).isTrue();
        assertThat(client.isThrottled(" { } ")).isTrue();
        assertThat(client.isThrottled("{\"header\":{\"resultCode\":\"00\"}}")).isFalse();
    }

    @Test
    @DisplayName("게이트웨이 오류는 스로틀이 아니다. 다시 물어도 같은 답이 온다")
    void doesNotMistakeGatewayErrorForThrottle() {
        String gatewayError = """
                {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
                "errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
                "returnAuthMsg":"등록되지 않은 서비스키","returnReasonCode":"30"}}}""";

        assertThat(client.isThrottled(gatewayError)).isFalse();
        assertThatThrownBy(() -> client.parseParkRltm(gatewayError))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("오류를 반환했습니다");
    }

    @Test
    @DisplayName("스로틀을 만나면 짧게 기다렸다 다시 묻는다")
    void retriesAfterThrottle() {
        Deque<String> responses = new ArrayDeque<>(List.of("{}", "{}", "{\"header\":{\"resultCode\":\"00\"}}"));

        String body = client.fetchWithThrottleRetry("getParkRltm", responses::poll);

        assertThat(body).contains("resultCode");
        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("계속 비어 있으면 빈 목록이 아니라 실패로 올린다")
    void failsWhenThrottleNeverClears() {
        assertThatThrownBy(() -> client.fetchWithThrottleRetry("getParkRltm", () -> "{}"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("호출 제한");
    }

    @Test
    @DisplayName("재시도 횟수를 0 으로 두면 한 번만 묻는다")
    void honorsRetryCount() {
        GnItsClient once = new GnItsClient(properties(Duration.ZERO, 0));
        int[] calls = {0};

        assertThatThrownBy(() -> once.fetchWithThrottleRetry("getParkRltm", () -> {
            calls[0]++;
            return "{}";
        })).isInstanceOf(ExternalApiException.class);

        assertThat(calls[0]).isEqualTo(1);
    }

    @Test
    @DisplayName("공급자 오류 코드는 외부 호출 실패로 바꾼다. 빈 주차장 목록으로 위장하지 않는다")
    void convertsProviderErrorToException() {
        String error = """
                {"header":{"resultCode":"11","resultMsg":"필수 파라미터 누락"}}""";

        assertThatThrownBy(() -> client.parseParkInfo(error))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("오류를 반환했습니다");
    }

    @Test
    @DisplayName("JSON 이 아닌 게이트웨이 응답도 외부 호출 실패로 바꾼다")
    void convertsXmlGatewayErrorToException() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <OpenAPI_ServiceResponse><cmmMsgHeader>
                <errMsg>NO_OPENAPI_SERVICE_ERROR</errMsg>
                <returnReasonCode>12</returnReasonCode>
                </cmmMsgHeader></OpenAPI_ServiceResponse>""";

        assertThatThrownBy(() -> client.parseParkRltm(xml))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("해석하지 못했습니다");
    }

    @Test
    @DisplayName("스로틀 본문을 해석하려 하면 호출 제한으로 구분해 알린다")
    void reportsThrottleWhenParsingEmptyBody() {
        assertThatThrownBy(() -> client.parseParkRltm("{}"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("호출 제한");
    }

    @Test
    @DisplayName("캐시 키는 같은 조건이면 항상 같고 인증키를 담지 않는다")
    void buildsStableCacheKey() {
        assertThat(client.parkRltmKey()).isEqualTo(client.parkRltmKey());
        assertThat(client.parkRltmKey()).isNotEqualTo(client.parkInfoKey());
        assertThat(client.parkRltmKey())
                .startsWith("getParkRltm?")
                .contains("pageNo=1")
                .contains("numOfRows=100")
                .doesNotContain("serviceKey");
    }

    @Test
    @DisplayName("인증키가 없으면 호출하지 않는다")
    void refusesWithoutServiceKey() {
        GnItsClient keyless = new GnItsClient(
                new GnItsProperties("http://example.invalid", " ", 100, 0, Duration.ZERO,
                        Duration.ofSeconds(1), Duration.ofSeconds(1)));

        assertThatThrownBy(keyless::parkRltmJson)
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("인증키");
    }
}
