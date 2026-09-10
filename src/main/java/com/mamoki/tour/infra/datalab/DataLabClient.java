package com.mamoki.tour.infra.datalab;

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
import com.mamoki.tour.infra.datalab.dto.DataLabResponse;

/**
 * 기초지자체 일별 방문자수 조회 클라이언트.
 *
 * <p>인증키는 KorService2 와 같은 공공데이터포털 Encoding 키다. 추가 인코딩 없이 그대로 붙인다.
 *
 * <p>이 API 는 지역 지정 파라미터를 받지 않는다. areaCd·signguCd·areaCode·signguCode 모두
 * INVALID_REQUEST_PARAMETER_ERROR 로 거절되고, 요청한 기간의 전국 시·군구가 통째로 온다.
 * 그래서 강원만 추리는 일은 호출부가 맡는다.
 */
@Component
public class DataLabClient {

    private static final String OPERATION = "locgoRegnVisitrDDList";

    private final DataLabProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DataLabClient(DataLabProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build();
    }

    public String regionVisitorsJson(LocalDate startDate, LocalDate endDate, int pageNo) {
        return fetchJson(params(startDate, endDate, pageNo));
    }

    public String regionVisitorsKey(LocalDate startDate, LocalDate endDate, int pageNo) {
        return OPERATION + "?" + params(startDate, endDate, pageNo).entrySet().stream()
                .filter(entry -> !"MobileApp".equals(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public DataLabResponse parse(String body) {
        DataLabResponse response;
        try {
            response = objectMapper.readValue(body, DataLabResponse.class);
        } catch (Exception e) {
            throw new ExternalApiException(ApiProvider.REGION_VISITOR,
                    "DataLabService 응답을 해석하지 못했습니다.", e);
        }

        if (response.response() == null || response.response().header() == null
                || !response.response().header().isSuccess()) {
            throw new ExternalApiException(ApiProvider.REGION_VISITOR,
                    "DataLabService 가 오류를 반환했습니다.");
        }

        return response;
    }

    private Map<String, String> params(LocalDate startDate, LocalDate endDate, int pageNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("MobileOS", "ETC");
        params.put("MobileApp", properties.mobileApp());
        params.put("_type", "json");
        params.put("numOfRows", String.valueOf(properties.rowsPerPage()));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("startYmd", DataLabProperties.format(startDate));
        params.put("endYmd", DataLabProperties.format(endDate));
        return params;
    }

    private String fetchJson(Map<String, String> params) {
        if (!properties.hasServiceKey()) {
            throw new ExternalApiException(ApiProvider.REGION_VISITOR,
                    "DataLabService 인증키가 설정되지 않았습니다.");
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
            throw new ExternalApiException(ApiProvider.REGION_VISITOR,
                    "DataLabService 호출에 실패했습니다.", e);
        }

        if (body == null || body.isBlank()) {
            throw new ExternalApiException(ApiProvider.REGION_VISITOR,
                    "DataLabService 응답이 비어 있습니다.");
        }

        return body;
    }

    private static ClientHttpRequestFactory requestFactory(DataLabProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());

        return factory;
    }
}
