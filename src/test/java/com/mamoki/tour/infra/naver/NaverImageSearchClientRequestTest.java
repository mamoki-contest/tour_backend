package com.mamoki.tour.infra.naver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.mamoki.tour.global.exception.ExternalApiException;

/**
 * 네이버 이미지 검색으로 나가는 요청의 모양과, 돌아온 상태 코드를 어떻게 가르는지 본다.
 *
 * <p>가르는 일이 중요한 까닭은 배치가 계속할지 멈출지를 여기서 받은 예외로 정하기
 * 때문이다. 인증 실패와 한도 초과는 남은 관광지를 계속 불러도 모두 같은 답을 받는다.
 *
 * <p>이 공급자에서 401 은 두 가지다 — 키가 잘못됐거나, 키는 맞는데 그 Application 에서
 * 이미지 검색이 켜져 있지 않거나. 둘 다 사람이 콘솔에서 고쳐야 하고 다시 불러도 같은
 * 답이라 구분해서 다루지 않는다. 다만 무엇을 확인해야 하는지는 메시지에 적는다.
 */
class NaverImageSearchClientRequestTest {

    private static final String BASE_URL = "https://naverapihub.apigw.ntruss.com";
    private static final String KEY_ID = "abcd123456";
    private static final String KEY = "secret-key-value";

    private MockRestServiceServer server;

    private NaverImageSearchClient client() {
        return client(KEY_ID, KEY);
    }

    private NaverImageSearchClient client(String keyId, String key) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        return new NaverImageSearchClient(
                new NaverApiHubProperties(BASE_URL, keyId, key,
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ZERO),
                builder);
    }

    private static String fixture() throws Exception {
        try (InputStream in = NaverImageSearchClientRequestTest.class
                .getResourceAsStream("/fixtures/naver-image-search.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("검색어·표시 수·필터·정렬을 실어 이미지 검색 경로로 보낸다")
    void sendsImageSearchRequest() throws Exception {
        NaverImageSearchClient client = client();

        server.expect(requestTo(BASE_URL + "/search/v1/image"
                        + "?query=%EA%B2%BD%ED%8F%AC%ED%95%B4%EB%B3%80+%EA%B0%95%EB%A6%89%EC%8B%9C"
                        + "&display=3&sort=sim&filter=medium"))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        assertThat(client.search("경포해변 강릉시", 3, "medium").firstUsable()).isPresent();
        server.verify();
    }

    @Test
    @DisplayName("NAVER API HUB 인증 헤더를 붙인다 - 개발자센터 헤더가 아니다")
    void sendsApiHubHeaders() throws Exception {
        NaverImageSearchClient client = client();

        server.expect(requestTo(Matchers.any(String.class)))
                .andExpect(header("X-NCP-APIGW-API-KEY-ID", KEY_ID))
                .andExpect(header("X-NCP-APIGW-API-KEY", KEY))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        client.search("경포해변 강릉시", 3, "medium");
        server.verify();
    }

    @Test
    @DisplayName("키가 없으면 한 번도 부르지 않고 인증 실패로 올린다")
    void doesNotCallWithoutCredentials() {
        NaverImageSearchClient client = client(null, null);

        assertThatThrownBy(() -> client.search("경포해변 강릉시", 3, "medium"))
                .isInstanceOf(NaverAuthenticationException.class);

        server.verify();
    }

    @Test
    @DisplayName("401 은 인증 실패다 - 이미지 검색 활성화 여부를 메시지로 알린다")
    void mapsUnauthorizedToAuthenticationFailure() {
        NaverImageSearchClient client = client();

        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":{\"errorCode\":401,"
                                + "\"message\":\"요청한 API는 이 Application에서 활성화되어 있지 않습니다.\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("경포해변 강릉시", 3, "medium"))
                .isInstanceOf(NaverAuthenticationException.class)
                .hasMessageContaining("이미지 검색");
    }

    @Test
    @DisplayName("403 도 인증 실패로 다룬다")
    void mapsForbiddenToAuthenticationFailure() {
        NaverImageSearchClient client = client();

        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.search("경포해변 강릉시", 3, "medium"))
                .isInstanceOf(NaverAuthenticationException.class);
    }

    @Test
    @DisplayName("429 는 한도 초과다")
    void mapsTooManyRequestsToRateLimit() {
        NaverImageSearchClient client = client();

        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.search("경포해변 강릉시", 3, "medium"))
                .isInstanceOf(NaverRateLimitException.class);
    }

    @Test
    @DisplayName("그 밖의 오류는 이 한 건의 실패다 - 배치를 멈추지 않는다")
    void mapsOtherErrorsToExternalApiException() {
        NaverImageSearchClient client = client();

        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.search("경포해변 강릉시", 3, "medium"))
                .isInstanceOf(ExternalApiException.class)
                .isNotInstanceOf(NaverAuthenticationException.class)
                .isNotInstanceOf(NaverRateLimitException.class);
    }

    @Test
    @DisplayName("본문이 비어 있으면 0건으로 읽지 않고 실패로 올린다")
    void doesNotReadEmptyBodyAsZeroResult() {
        NaverImageSearchClient client = client();

        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("경포해변 강릉시", 3, "medium"))
                .isInstanceOf(ExternalApiException.class);
    }
}
