package com.mamoki.tour.infra.naver;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.naver.dto.NaverBlogSearchResponse;

/**
 * 네이버 블로그 검색 호출 클라이언트.
 *
 * <p>한 번의 호출이 관광지 한 곳의 언급량을 만든다. 검색 결과 목록은 필요 없고 {@code total}
 * 만 쓰므로 {@code display} 를 최소로 요청한다.
 *
 * <p>인증 실패와 한도 초과는 다른 실패와 구분해서 던진다. 배치가 남은 호출을 계속할지
 * 즉시 멈출지 판단해야 하기 때문이다.
 */
@Component
public class NaverBlogSearchClient {

    /** total 만 쓰므로 목록은 최소로 받는다. */
    private static final int MIN_DISPLAY = 1;

    private final NaverApiHubProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NaverBlogSearchClient(NaverApiHubProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build();
    }

    /**
     * @throws NaverAuthenticationException 인증 실패. 남은 호출을 계속해도 모두 실패한다.
     * @throws NaverRateLimitException      호출 한도 초과.
     * @throws ExternalApiException         그 밖의 호출·해석 실패.
     */
    public NaverBlogSearchResponse search(String query) {
        if (!properties.hasCredentials()) {
            throw new NaverAuthenticationException("네이버 API HUB 인증 정보가 설정되지 않았습니다.");
        }

        URI uri = URI.create(properties.baseUrl() + "/search/v1/blog"
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&display=" + MIN_DISPLAY);

        String body;
        try {
            body = restClient.get()
                    .uri(uri)
                    .header("X-NCP-APIGW-API-KEY-ID", properties.keyId())
                    .header("X-NCP-APIGW-API-KEY", properties.key())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw toException(response.getStatusCode(), query);
                    })
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ExternalApiException(ApiProvider.NAVER_BLOG_SEARCH,
                    "네이버 블로그 검색 호출에 실패했습니다: " + query, e);
        }

        return parse(body, query);
    }

    private static RuntimeException toException(HttpStatusCode status, String query) {
        if (status.value() == 401 || status.value() == 403) {
            return new NaverAuthenticationException(
                    "네이버 API HUB 인증에 실패했습니다 (HTTP %d).".formatted(status.value()));
        }

        if (status.value() == 429) {
            return new NaverRateLimitException("네이버 API HUB 호출 한도를 초과했습니다.");
        }

        return new ExternalApiException(ApiProvider.NAVER_BLOG_SEARCH,
                "네이버 블로그 검색이 오류를 반환했습니다 (HTTP %d): %s".formatted(status.value(), query));
    }

    private NaverBlogSearchResponse parse(String body, String query) {
        if (body == null || body.isBlank()) {
            throw new ExternalApiException(ApiProvider.NAVER_BLOG_SEARCH,
                    "네이버 블로그 검색 응답이 비어 있습니다: " + query);
        }

        NaverBlogSearchResponse response;
        try {
            response = objectMapper.readValue(body, NaverBlogSearchResponse.class);
        } catch (Exception e) {
            throw new ExternalApiException(ApiProvider.NAVER_BLOG_SEARCH,
                    "네이버 블로그 검색 응답을 해석하지 못했습니다: " + query, e);
        }

        // total 이 없으면 0 으로 채우지 않는다. 값 없음과 0건은 다르다.
        if (!response.hasTotal()) {
            throw new ExternalApiException(ApiProvider.NAVER_BLOG_SEARCH,
                    "네이버 블로그 검색 응답에 total 이 없습니다: " + query);
        }

        return response;
    }

    private static ClientHttpRequestFactory requestFactory(NaverApiHubProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());

        return factory;
    }
}
