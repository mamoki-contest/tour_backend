package com.mamoki.tour.infra.kakao;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.kakao.dto.KakaoKeywordSearchResponse;

/**
 * 카카오 로컬 키워드 검색 호출 클라이언트.
 *
 * <p>한 번의 호출이 원천 이름 하나의 후보 장소를 만든다. 검색어만으로 부르면 같은 이름이
 * 전국에 흩어져 있어 대상 시·군의 장소가 첫 페이지에서 밀려난다. 그래서 시·군 중심 좌표와
 * 반경을 함께 보내 그 주변을 먼저 보게 한다.
 *
 * <p>인증 실패와 한도 초과는 다른 실패와 구분해서 던진다. 둘 다 남은 호출을 계속해도 얻는 것
 * 없이 한도만 깎는 상황이라, 배치가 즉시 멈춰야 하는지 판단해야 하기 때문이다.
 */
@Component
public class KakaoLocalClient {

    private static final String KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json";

    private final KakaoLocalProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * 빌더는 밖에서 받는다. 계약 테스트가 {@code MockRestServiceServer} 를 붙인 빌더를 그대로
     * 넘겨 실제 호출 없이 응답을 주입한다. 넘겨받은 빌더의 요청 경로는 그대로 둔다.
     */
    public KakaoLocalClient(
            KakaoLocalProperties properties,
            @Qualifier("kakaoLocalRestClientBuilder") RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.restClient = restClientBuilder.build();
    }

    public boolean hasCredentials() {
        return properties.hasCredentials();
    }

    /** 호출 사이에 둘 간격. 배치가 한도를 짧은 시간에 소진하지 않도록 지킨다. */
    public Duration callDelay() {
        return properties.callDelay();
    }

    /**
     * 시·군 주변에서 이름으로 장소를 찾는다.
     *
     * @param query     찾을 이름. 원천 표기 그대로 보낸다.
     * @param latitude  검색 중심 위도. null 이면 좌표 없이 이름만으로 찾는다.
     * @param longitude 검색 중심 경도. null 이면 좌표 없이 이름만으로 찾는다.
     * @throws KakaoAuthenticationException 인증 실패. 남은 호출을 계속해도 모두 실패한다.
     * @throws KakaoRateLimitException      호출 한도 초과.
     * @throws ExternalApiException         그 밖의 호출·해석 실패.
     */
    public KakaoKeywordSearchResponse searchKeyword(String query,
                                                    BigDecimal latitude, BigDecimal longitude) {
        if (!properties.hasCredentials()) {
            throw new KakaoAuthenticationException("카카오 REST API 키가 설정되지 않았습니다.");
        }

        String body;
        try {
            body = restClient.get()
                    .uri(uri(query, latitude, longitude))
                    .header("Authorization", "KakaoAK " + properties.restApiKey())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw toException(response.getStatusCode(), query);
                    })
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ExternalApiException(ApiProvider.KAKAO_LOCAL,
                    "카카오 로컬 키워드 검색 호출에 실패했습니다: " + query, e);
        }

        return parse(body, query);
    }

    private URI uri(String query, BigDecimal latitude, BigDecimal longitude) {
        StringBuilder uri = new StringBuilder(properties.baseUrl())
                .append(KEYWORD_SEARCH_PATH)
                .append("?query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8))
                .append("&size=").append(properties.size());

        // 좌표를 모르는 시·군은 이름만으로 찾는다. 중심을 지어내 엉뚱한 주변을 먼저 보게 하지 않는다.
        if (latitude != null && longitude != null) {
            uri.append("&x=").append(longitude.toPlainString())
                    .append("&y=").append(latitude.toPlainString())
                    .append("&radius=").append(properties.searchRadius());
        }

        return URI.create(uri.toString());
    }

    /**
     * HTTP 상태를 배치가 다룰 수 있는 예외로 옮긴다.
     *
     * <p>카카오는 잘못된 키에 401, 한도 초과에 429 를 준다. 403 은 이 앱에 로컬 API 권한이
     * 없는 경우라 인증 실패와 같이 다룬다. 어느 쪽이든 다음 호출도 같은 답을 받는다.
     */
    static RuntimeException toException(HttpStatusCode status, String query) {
        if (status.value() == 401 || status.value() == 403) {
            return new KakaoAuthenticationException(
                    "카카오 로컬 API 인증에 실패했습니다 (HTTP %d).".formatted(status.value()));
        }

        if (status.value() == 429) {
            return new KakaoRateLimitException("카카오 로컬 API 호출 한도를 초과했습니다.");
        }

        return new ExternalApiException(ApiProvider.KAKAO_LOCAL,
                "카카오 로컬 키워드 검색이 오류를 반환했습니다 (HTTP %d): %s"
                        .formatted(status.value(), query));
    }

    private KakaoKeywordSearchResponse parse(String body, String query) {
        if (body == null || body.isBlank()) {
            throw new ExternalApiException(ApiProvider.KAKAO_LOCAL,
                    "카카오 로컬 키워드 검색 응답이 비어 있습니다: " + query);
        }

        try {
            return objectMapper.readValue(body, KakaoKeywordSearchResponse.class);
        } catch (Exception e) {
            throw new ExternalApiException(ApiProvider.KAKAO_LOCAL,
                    "카카오 로컬 키워드 검색 응답을 해석하지 못했습니다: " + query, e);
        }
    }
}
