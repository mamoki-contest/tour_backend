package com.mamoki.tour.infra.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.kakao.dto.KakaoKeywordSearchResponse;
import com.mamoki.tour.infra.kakao.dto.KakaoPlace;

/**
 * 저장한 실제 응답으로 카카오 로컬 키워드 검색 계약을 검증한다.
 *
 * <p>fixture 는 {@code 경포해변} 을 강릉 중심 좌표 반경 20km 로 실제 조회한 응답이다
 * (2026-09-20). 이 이름이 카탈로그의 {@code 경포해수욕장} 과 달라 매핑이 필요했던 바로 그
 * 사례라서 골랐다.
 */
class KakaoLocalContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private KakaoKeywordSearchResponse fixture() throws Exception {
        try (InputStream in = getClass()
                .getResourceAsStream("/fixtures/kakao-local-search-keyword.json")) {
            return objectMapper.readValue(in, KakaoKeywordSearchResponse.class);
        }
    }

    @Test
    @DisplayName("매핑에 쓰는 장소 id·이름·좌표를 읽는다")
    void readsPlaceIdentityAndCoordinate() throws Exception {
        KakaoPlace first = fixture().documents().get(0);

        assertThat(first.id()).isEqualTo("8199114");
        assertThat(first.placeName()).isEqualTo("경포해수욕장");
        assertThat(first.categoryGroupCode()).isEqualTo("AT4");
        assertThat(first.addressName()).startsWith("강원특별자치도 강릉시");
    }

    @Test
    @DisplayName("x 는 경도, y 는 위도로 읽는다")
    void readsLongitudeAsXAndLatitudeAsY() throws Exception {
        KakaoPlace first = fixture().documents().get(0);

        // 강릉은 위도 37 도대, 경도 128 도대다. 둘을 뒤집으면 거리 판정이 통째로 어긋난다.
        assertThat(first.latitude()).isNotNull();
        assertThat(first.latitude().doubleValue()).isBetween(37.0, 38.0);
        assertThat(first.longitude()).isNotNull();
        assertThat(first.longitude().doubleValue()).isBetween(128.0, 129.0);
    }

    @Test
    @DisplayName("검색어와 이름이 달라도 같은 장소를 가리키는 응답을 받는다")
    void findsPlaceWhoseNameDiffersFromQuery() throws Exception {
        KakaoKeywordSearchResponse response = fixture();

        assertThat(response.documents()).hasSize(15);
        assertThat(response.documents().get(0).placeName()).isNotEqualTo("경포해변");
    }

    @Test
    @DisplayName("좌표가 숫자가 아니면 값을 만들어내지 않는다")
    void doesNotInventCoordinate() throws Exception {
        KakaoKeywordSearchResponse response = objectMapper.readValue("""
                {"documents":[{"id":"1","place_name":"어딘가","x":"","y":"알수없음"}]}
                """, KakaoKeywordSearchResponse.class);

        KakaoPlace place = response.documents().get(0);

        assertThat(place.latitude()).isNull();
        assertThat(place.longitude()).isNull();
        assertThat(place.hasCoordinate()).isFalse();
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 목록이다")
    void emptyDocumentsWhenNothingFound() throws Exception {
        KakaoKeywordSearchResponse response = objectMapper.readValue(
                "{\"documents\":[],\"meta\":{\"total_count\":0}}",
                KakaoKeywordSearchResponse.class);

        assertThat(response.documents()).isEmpty();
    }

    @Test
    @DisplayName("모르는 필드가 늘어나도 깨지지 않는다")
    void toleratesUnknownFields() throws Exception {
        KakaoKeywordSearchResponse response = objectMapper.readValue("""
                {"documents":[{"id":"1","place_name":"어딘가","x":"128.0","y":"37.0","newField":"x"}],
                 "meta":{"total_count":1,"newMeta":true}}
                """, KakaoKeywordSearchResponse.class);

        assertThat(response.documents().get(0).placeName()).isEqualTo("어딘가");
    }

    @Test
    @DisplayName("documents 가 아예 없어도 빈 목록으로 다룬다")
    void missingDocumentsBecomesEmptyList() throws Exception {
        KakaoKeywordSearchResponse response =
                objectMapper.readValue("{\"meta\":{\"total_count\":0}}",
                        KakaoKeywordSearchResponse.class);

        assertThat(response.documents()).isEmpty();
    }

    /**
     * 요청 모양과 실패 응답 처리.
     *
     * <p>실제 호출 없이 확인한다. 인증 실패·한도 초과는 배치가 즉시 멈춰야 하는 신호라
     * 다른 실패와 같은 예외로 뭉뚱그리면 남은 이름을 계속 부르며 한도만 깎게 된다.
     */
    @Nested
    @DisplayName("클라이언트")
    class ClientContract {

        private static final String BASE_URL = "https://dapi.kakao.com";

        private MockRestServiceServer server;
        private KakaoLocalClient client;

        private KakaoLocalClient client(String restApiKey) {
            RestClient.Builder builder = RestClient.builder();
            server = MockRestServiceServer.bindTo(builder).build();
            client = new KakaoLocalClient(
                    new KakaoLocalProperties(BASE_URL, restApiKey,
                            Duration.ofSeconds(5), Duration.ofSeconds(15),
                            Duration.ofMillis(100), 15, 20_000),
                    builder);

            return client;
        }

        private String fixtureBody() throws Exception {
            return new String(new ClassPathResource("fixtures/kakao-local-search-keyword.json")
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("REST API 키를 KakaoAK 헤더로 보내고 시·군 주변으로 좁힌다")
        void sendsAuthorizationHeaderAndLocationBias() throws Exception {
            KakaoLocalClient client = client("test-rest-api-key");
            server.expect(requestTo(Matchers.startsWith(BASE_URL + "/v2/local/search/keyword.json")))
                    .andExpect(header("Authorization", "KakaoAK test-rest-api-key"))
                    .andExpect(queryParam("x", "128.8761"))
                    .andExpect(queryParam("y", "37.7519"))
                    .andExpect(queryParam("radius", "20000"))
                    .andRespond(withSuccess(fixtureBody(), MediaType.APPLICATION_JSON));

            KakaoKeywordSearchResponse response = client.searchKeyword(
                    "경포해변", new BigDecimal("37.7519"), new BigDecimal("128.8761"));

            assertThat(response.documents().get(0).placeName()).isEqualTo("경포해수욕장");
            server.verify();
        }

        @Test
        @DisplayName("중심 좌표를 모르면 좌표 없이 이름만으로 찾는다")
        void omitsLocationBiasWithoutCenter() throws Exception {
            KakaoLocalClient client = client("test-rest-api-key");
            server.expect(requestTo(Matchers.not(Matchers.containsString("radius"))))
                    .andRespond(withSuccess(fixtureBody(), MediaType.APPLICATION_JSON));

            assertThat(client.searchKeyword("경포해변", null, null).documents()).isNotEmpty();
            server.verify();
        }

        @Test
        @DisplayName("인증에 실패하면 계속 불러도 소용없다는 것을 구분해서 알린다")
        void distinguishesAuthenticationFailure() {
            KakaoLocalClient client = client("wrong-key");
            server.expect(requestTo(Matchers.any(String.class)))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                            .body("{\"errorType\":\"AccessDeniedError\",\"message\":\"wrong appKey\"}")
                            .contentType(MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.searchKeyword("경포해변", null, null))
                    .isInstanceOf(KakaoAuthenticationException.class);
        }

        @Test
        @DisplayName("한도를 초과하면 인증 실패와 구분해서 알린다")
        void distinguishesRateLimit() {
            KakaoLocalClient client = client("test-rest-api-key");
            server.expect(requestTo(Matchers.any(String.class)))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .body("{\"errorType\":\"RequestThrottled\"}")
                            .contentType(MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.searchKeyword("경포해변", null, null))
                    .isInstanceOf(KakaoRateLimitException.class);
        }

        @Test
        @DisplayName("그 밖의 오류는 일반 외부 API 실패로 남긴다")
        void otherErrorsStayGeneric() {
            KakaoLocalClient client = client("test-rest-api-key");
            server.expect(requestTo(Matchers.any(String.class)))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> client.searchKeyword("경포해변", null, null))
                    .isInstanceOf(ExternalApiException.class)
                    .isNotInstanceOf(KakaoAuthenticationException.class)
                    .isNotInstanceOf(KakaoRateLimitException.class);
        }

        @Test
        @DisplayName("키가 비어 있으면 호출을 내지 않고 끝낸다")
        void refusesWithoutKey() {
            KakaoLocalClient client = client("");

            assertThatThrownBy(() -> client.searchKeyword("경포해변", null, null))
                    .isInstanceOf(KakaoAuthenticationException.class);

            // 호출이 하나도 나가지 않아야 한다. 키 없이 부르면 한도만 깎고 모두 401 을 받는다.
            server.verify();
        }
    }
}
