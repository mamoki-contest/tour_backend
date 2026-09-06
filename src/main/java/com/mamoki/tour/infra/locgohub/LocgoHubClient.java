package com.mamoki.tour.infra.locgohub;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.locgohub.dto.LocgoHubResponse;

/**
 * 시·군 중심관광지 순위 조회 클라이언트.
 *
 * <p>인증키는 KorService2 와 같은 공공데이터포털 Encoding 키다. 추가 인코딩 없이 그대로 붙인다.
 *
 * <p>지역 지정에 KorService2 의 areaCode/sigunguCode 가 아니라 법정동 코드를 쓴다.
 * 두 체계의 변환은 region_code 테이블이 담당한다.
 */
@Component
public class LocgoHubClient {

    private static final String OPERATION = "areaBasedList1";

    private final LocgoHubProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public LocgoHubClient(LocgoHubProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build();
    }

    /**
     * @param lawdCode 법정동 시·군 코드 5자리 (예: 51150)
     */
    public String areaBasedListJson(String lawdCode, int numOfRows) {
        return fetchJson(params(lawdCode, numOfRows));
    }

    public String areaBasedListKey(String lawdCode, int numOfRows) {
        return OPERATION + "?" + params(lawdCode, numOfRows).entrySet().stream()
                .filter(entry -> !"MobileApp".equals(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public LocgoHubResponse parse(String body) {
        LocgoHubResponse response;
        try {
            response = objectMapper.readValue(body, LocgoHubResponse.class);
        } catch (Exception e) {
            throw new ExternalApiException(ApiProvider.LOCGO_HUB_TAR,
                    "LocgoHubTarService1 응답을 해석하지 못했습니다.", e);
        }

        if (response.response() == null || response.response().header() == null
                || !response.response().header().isSuccess()) {
            throw new ExternalApiException(ApiProvider.LOCGO_HUB_TAR,
                    "LocgoHubTarService1 이 오류를 반환했습니다.");
        }

        return response;
    }

    /** 법정동 시·군 코드는 시·도 코드 2자리와 시·군 코드 3자리로 나누어 전달한다. */
    private Map<String, String> params(String lawdCode, int numOfRows) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("MobileOS", "ETC");
        params.put("MobileApp", properties.mobileApp());
        params.put("_type", "json");
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", "1");
        params.put("baseYm", properties.resolveBaseYm(LocalDate.now()));
        params.put("areaCd", lawdCode.substring(0, 2));
        params.put("signguCd", lawdCode);
        return params;
    }

    private String fetchJson(Map<String, String> params) {
        if (!properties.hasServiceKey()) {
            throw new ExternalApiException(ApiProvider.LOCGO_HUB_TAR,
                    "LocgoHubTarService1 인증키가 설정되지 않았습니다.");
        }

        String query = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        URI uri = URI.create(properties.baseUrl() + "/" + OPERATION
                + "?serviceKey=" + properties.serviceKey() + "&" + query);

        String body;
        try {
            body = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new ExternalApiException(ApiProvider.LOCGO_HUB_TAR,
                    "LocgoHubTarService1 호출에 실패했습니다.", e);
        }

        if (body == null || body.isBlank()) {
            throw new ExternalApiException(ApiProvider.LOCGO_HUB_TAR,
                    "LocgoHubTarService1 응답이 비어 있습니다.");
        }

        return body;
    }

    private static ClientHttpRequestFactory requestFactory(LocgoHubProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());

        return factory;
    }
}
