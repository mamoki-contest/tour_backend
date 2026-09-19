package com.mamoki.tour.infra.korservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.mamoki.tour.global.exception.ExternalApiException;

/**
 * KorService2 가 실제로 내보내는 요청의 모양을 확인한다.
 *
 * <p>fixture 로 응답 해석을 검증하는 {@link KorServiceContractTest} 와 하는 일이 다르다.
 * 여기서 보는 것은 <b>우리가 무엇을 보내는가</b>이고, 그중에서도 인증키다.
 *
 * <p>공공데이터포털의 Encoding 키는 이미 URL 인코딩된 문자열이다({@code %2F}, {@code %2B},
 * {@code %3D} 를 품고 있다). 질의 문자열을 만들면서 이 값까지 한 번 더 인코딩하면
 * {@code %2F} 가 {@code %252F} 가 되어 공급자가 인증을 거부한다. 그래서
 * {@link KorServiceClient} 는 serviceKey 만 인코딩하지 않고 그대로 붙인다(README 외부 API 절).
 *
 * <p>이 규칙은 지금까지 호출 수준에서 확인할 자리가 없었다(#66). 클라이언트가 생성자 안에서
 * {@code RestClient} 를 직접 만들어 {@code MockRestServiceServer} 를 붙일 수 없었기 때문이다.
 * 빌더를 밖에서 받게 되면서 나가는 URI 를 선언적으로 검사할 수 있게 됐다.
 */
class KorServiceClientRequestTest {

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";

    /**
     * 공공데이터포털 Encoding 키의 모양을 그대로 흉내 낸 가짜 키.
     * 이중 인코딩이 일어나면 {@code %2F} → {@code %252F} 로 눈에 띈다.
     */
    private static final String ENCODING_KEY = "AbC%2FdEf%2BgHi%3D%3D";

    private MockRestServiceServer server;
    private KorServiceClient client;

    @BeforeEach
    void setUp() {
        client = client(BASE_URL);
    }

    private KorServiceClient client(String baseUrl) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        return new KorServiceClient(
                new KorServiceProperties(baseUrl, ENCODING_KEY, "tour",
                        Duration.ofSeconds(1), Duration.ofSeconds(1)),
                builder);
    }

    private static String fixture() throws Exception {
        try (InputStream in = KorServiceClientRequestTest.class
                .getResourceAsStream("/fixtures/korservice-areaBasedList2.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("serviceKey 는 받은 Encoding 키 그대로 나간다 — 다시 인코딩하지 않는다")
    void sendsServiceKeyWithoutReEncoding() throws Exception {
        server.expect(requestTo(BASE_URL + "/areaBasedList2"
                        + "?serviceKey=" + ENCODING_KEY
                        + "&MobileOS=ETC&MobileApp=tour&_type=json"
                        + "&numOfRows=5&pageNo=1&areaCode=32"))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        assertThat(client.areaBasedList("32", null, null, 1, 5).totalCount()).isPositive();
        server.verify();
    }

    /**
     * 위 테스트가 전체 URI 를 못 박고 있어 이중 인코딩이면 이미 깨진다. 그래도 한 번 더
     * 적어 두는 것은 <b>무엇이 틀렸는지</b>를 실패 메시지가 바로 말하게 하기 위해서다.
     * 전체 비교는 "URI 가 다르다"까지만 알려준다.
     */
    @Test
    @DisplayName("이중 인코딩의 흔적(%252F)이 URI 에 없다")
    void neverDoubleEncodesServiceKey() throws Exception {
        server.expect(requestTo(Matchers.not(Matchers.containsString("%252F"))))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        client.areaBasedList("32", null, null, 1, 5);
        server.verify();
    }

    @Test
    @DisplayName("serviceKey 가 아닌 값은 인코딩해서 보낸다")
    void encodesEveryOtherParameter() {
        server.expect(requestTo(Matchers.allOf(
                        Matchers.containsString("serviceKey=" + ENCODING_KEY),
                        Matchers.containsString("keyword=%EA%B2%BD%ED%8F%AC%ED%95%B4%EB%B3%80"))))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.searchKeywordByLawdJson("51", null, null, "경포해변", 1, 10);
        server.verify();
    }

    @Test
    @DisplayName("base-url 을 바꾸면 그 주소로 나간다")
    void honoursBaseUrlOverride() throws Exception {
        KorServiceClient overridden = client("http://127.0.0.1:19999/stub");

        server.expect(requestTo(Matchers.startsWith("http://127.0.0.1:19999/stub/areaBasedList2?")))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        assertThat(overridden.areaBasedList("32", null, null, 1, 5).totalCount()).isPositive();
        server.verify();
    }

    @Test
    @DisplayName("공급자 오류는 외부 호출 실패로 바뀐다. 빈 결과로 위장하지 않는다")
    void turnsProviderErrorIntoExternalApiException() {
        server.expect(requestTo(Matchers.any(String.class))).andRespond(withServerError());

        assertThatThrownBy(() -> client.areaBasedList("32", null, null, 1, 5))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("KorService2");
    }

    @Test
    @DisplayName("인증키가 없으면 호출하지 않는다")
    void refusesWithoutServiceKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer keylessServer = MockRestServiceServer.bindTo(builder).build();
        KorServiceClient keyless = new KorServiceClient(
                new KorServiceProperties(BASE_URL, " ", "tour",
                        Duration.ofSeconds(1), Duration.ofSeconds(1)),
                builder);

        assertThatThrownBy(() -> keyless.areaBasedList("32", null, null, 1, 5))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("인증키");

        keylessServer.verify();
    }
}
