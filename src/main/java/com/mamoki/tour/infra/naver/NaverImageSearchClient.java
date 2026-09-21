package com.mamoki.tour.infra.naver;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.naver.dto.NaverImageSearchResponse;

/**
 * 네이버 이미지 검색 호출 클라이언트.
 *
 * <p>블로그 검색과 같은 NAVER API HUB 게이트웨이·같은 키를 쓴다. 다른 것은 경로
 * ({@code /search/v1/image}) 와, 응답에서 꺼내는 값뿐이다.
 *
 * <p><b>키가 맞아도 401 이 올 수 있다.</b> 허브는 Application 마다 어떤 API 를 쓸지 따로
 * 켜는데, 이미지 검색이 꺼져 있으면 {@code errorCode 401 / "요청한 API는 이 Application에서
 * 활성화되어 있지 않습니다."} 를 준다(키가 틀렸을 때는 {@code errorCode 200 /
 * "Authentication Failed"}). 둘 다 사람이 콘솔에서 고쳐야 하고 다시 불러도 같은 답이라
 * 배치에게는 같은 지시(즉시 중단)이지만, 무엇을 확인해야 하는지는 메시지에 적어 둔다.
 *
 * <p>정렬은 관련도({@code sort=sim}) 로 고정한다. 우리가 찾는 것은 그 장소를 대표하는 한
 * 장이지 가장 최근에 올라온 사진이 아니다.
 */
@Component
public class NaverImageSearchClient {

    /** 관련도순. 최신순(date)으로 두면 그 장소와 상관없는 어제 사진이 1위가 된다. */
    private static final String SORT_BY_RELEVANCE = "sim";

    private final NaverApiHubProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NaverImageSearchClient(
            NaverApiHubProperties properties,
            @Qualifier("naverImageSearchRestClientBuilder") RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.restClient = restClientBuilder.build();
    }

    /**
     * @param display 받을 항목 수. 첫 항목이 주소 없는 껍데기일 수 있어 몇 장을 함께 받는다.
     * @param filter  이미지 크기 필터({@code all}/{@code large}/{@code medium}/{@code small}).
     * @throws NaverAuthenticationException 인증 실패 또는 이미지 검색 미활성. 남은 호출을
     *                                      계속해도 모두 같은 답이다.
     * @throws NaverRateLimitException      호출 한도 초과.
     * @throws ExternalApiException         그 밖의 호출·해석 실패. 이 한 건의 실패다.
     */
    public NaverImageSearchResponse search(String query, int display, String filter) {
        if (!properties.hasCredentials()) {
            throw new NaverAuthenticationException(ApiProvider.NAVER_IMAGE_SEARCH,
                    "네이버 API HUB 인증 정보가 설정되지 않았습니다.");
        }

        URI uri = URI.create(properties.baseUrl() + "/search/v1/image"
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&display=" + display
                + "&sort=" + SORT_BY_RELEVANCE
                + "&filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8));

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
            throw new ExternalApiException(ApiProvider.NAVER_IMAGE_SEARCH,
                    "네이버 이미지 검색 호출에 실패했습니다: " + query, e);
        }

        return parse(body, query);
    }

    private static RuntimeException toException(HttpStatusCode status, String query) {
        if (status.value() == 401 || status.value() == 403) {
            return new NaverAuthenticationException(ApiProvider.NAVER_IMAGE_SEARCH,
                    ("네이버 API HUB 인증에 실패했습니다 (HTTP %d). 키가 잘못되었거나, "
                            + "이 Application 에서 이미지 검색 API 가 활성화되어 있지 않습니다.")
                            .formatted(status.value()));
        }

        if (status.value() == 429) {
            return new NaverRateLimitException(ApiProvider.NAVER_IMAGE_SEARCH,
                    "네이버 API HUB 호출 한도를 초과했습니다.");
        }

        return new ExternalApiException(ApiProvider.NAVER_IMAGE_SEARCH,
                "네이버 이미지 검색이 오류를 반환했습니다 (HTTP %d): %s".formatted(status.value(), query));
    }

    private NaverImageSearchResponse parse(String body, String query) {
        if (body == null || body.isBlank()) {
            throw new ExternalApiException(ApiProvider.NAVER_IMAGE_SEARCH,
                    "네이버 이미지 검색 응답이 비어 있습니다: " + query);
        }

        try {
            return objectMapper.readValue(body, NaverImageSearchResponse.class);
        } catch (Exception e) {
            throw new ExternalApiException(ApiProvider.NAVER_IMAGE_SEARCH,
                    "네이버 이미지 검색 응답을 해석하지 못했습니다: " + query, e);
        }
    }
}
