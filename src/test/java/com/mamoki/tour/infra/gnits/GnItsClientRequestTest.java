package com.mamoki.tour.infra.gnits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.mamoki.tour.global.exception.ExternalApiException;

/**
 * 강릉시 교통정보 조회서비스로 나가는 요청의 모양을 확인한다.
 *
 * <p>fixture 로 응답 해석을 검증하는 {@link GnItsContractTest} 와 하는 일이 다르다.
 * 여기서 보는 것은 <b>우리가 무엇을 보내는가</b>이다.
 *
 * <p>이 공급자는 KorService2 와 같은 공공데이터포털 Encoding 키를 쓴다(계정당 키가 하나다).
 * 그래서 이중 인코딩 금지 규칙도 똑같이 적용된다 — 이미 인코딩된 serviceKey 를 한 번 더
 * 인코딩하면 {@code %2F} 가 {@code %252F} 가 되어 인증이 거부된다. 기관코드가 달라 base-url
 * 만 따로 둔다.
 *
 * <p>덤으로 조용한 스로틀 재시도도 여기서 선언적으로 본다. 빈 응답을 몇 번 돌려줄지 목이
 * 정하므로, 실제 서버를 띄웠을 때는 재현하기 어려웠던 갈래다(#66).
 */
class GnItsClientRequestTest {

    private static final String BASE_URL =
            "https://apis.data.go.kr/4201000/GNitsTrafficInfoService_1.0";

    /** 공공데이터포털 Encoding 키의 모양을 그대로 흉내 낸 가짜 키. */
    private static final String ENCODING_KEY = "AbC%2FdEf%2BgHi%3D%3D";

    private MockRestServiceServer server;

    private GnItsClient client(String baseUrl, int throttleRetries) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        return new GnItsClient(
                new GnItsProperties(baseUrl, ENCODING_KEY, 100, throttleRetries, Duration.ZERO,
                        Duration.ofSeconds(1), Duration.ofSeconds(1)),
                builder);
    }

    private static String fixture(String name) throws Exception {
        try (InputStream in = GnItsClientRequestTest.class
                .getResourceAsStream("/fixtures/" + name + ".json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("serviceKey 는 받은 Encoding 키 그대로 나간다 — 다시 인코딩하지 않는다")
    void sendsServiceKeyWithoutReEncoding() throws Exception {
        GnItsClient client = client(BASE_URL, 0);

        server.expect(requestTo(BASE_URL + "/getParkInfo"
                        + "?serviceKey=" + ENCODING_KEY
                        + "&pageNo=1&numOfRows=100"))
                .andRespond(withSuccess(fixture("gnits-getParkInfo"), MediaType.APPLICATION_JSON));

        assertThat(client.parseParkInfo(client.parkInfoJson()).items()).isNotEmpty();
        server.verify();
    }

    @Test
    @DisplayName("이중 인코딩의 흔적(%252F)이 URI 에 없다")
    void neverDoubleEncodesServiceKey() throws Exception {
        GnItsClient client = client(BASE_URL, 0);

        server.expect(requestTo(Matchers.not(Matchers.containsString("%252F"))))
                .andRespond(withSuccess(fixture("gnits-getParkRltm"), MediaType.APPLICATION_JSON));

        client.parkRltmJson();
        server.verify();
    }

    @Test
    @DisplayName("base-url 을 바꾸면 그 주소로 나간다")
    void honoursBaseUrlOverride() throws Exception {
        GnItsClient client = client("http://127.0.0.1:19999/stub", 0);

        server.expect(requestTo(Matchers.startsWith("http://127.0.0.1:19999/stub/getParkInfo?")))
                .andRespond(withSuccess(fixture("gnits-getParkInfo"), MediaType.APPLICATION_JSON));

        client.parkInfoJson();
        server.verify();
    }

    @Test
    @DisplayName("빈 응답(조용한 스로틀)을 만나면 같은 주소로 다시 묻는다")
    void retriesOnSilentThrottle() throws Exception {
        GnItsClient client = client(BASE_URL, 1);

        server.expect(requestTo(Matchers.containsString("/getParkRltm?")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.containsString("/getParkRltm?")))
                .andRespond(withSuccess(fixture("gnits-getParkRltm"), MediaType.APPLICATION_JSON));

        assertThat(client.parseParkRltm(client.parkRltmJson()).items()).isNotEmpty();
        server.verify();
    }

    @Test
    @DisplayName("끝내 비어 있으면 빈 목록이 아니라 예외로 올린다")
    void failsWhenThrottleNeverClears() {
        GnItsClient client = client(BASE_URL, 1);

        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::parkRltmJson)
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("호출 제한");

        server.verify();
    }

    @Test
    @DisplayName("공급자 오류는 외부 호출 실패로 바뀐다")
    void turnsProviderErrorIntoExternalApiException() {
        GnItsClient client = client(BASE_URL, 0);

        server.expect(requestTo(Matchers.any(String.class))).andRespond(withServerError());

        assertThatThrownBy(client::parkInfoJson)
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("강릉시 교통정보 조회서비스");
    }
}
