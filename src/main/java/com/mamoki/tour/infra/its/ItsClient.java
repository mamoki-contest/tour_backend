package com.mamoki.tour.infra.its;

import java.math.BigDecimal;
import java.net.URI;
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
import com.mamoki.tour.infra.its.dto.ItsResponse;

/**
 * 교통소통정보 조회 클라이언트.
 *
 * <p>좌표 사각형으로 조회한다. 관광지 하나를 물어보는 API 가 아니라 영역 안의 도로 구간을
 * 돌려주는 API 라, 관광지 좌표를 중심으로 작은 사각형을 만들어 부른다.
 *
 * <p>인증키는 공공데이터포털과 다른 포털에서 발급받은 값이라 별도 설정으로 둔다.
 * 질의 문자열에 그대로 붙이며 추가 인코딩을 하지 않는다.
 */
@Component
public class ItsClient {

    private static final String OPERATION = "trafficInfo";

    private final ItsProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ItsClient(ItsProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build();
    }

    public String trafficInfoJson(BigDecimal latitude, BigDecimal longitude) {
        return fetchJson(params(latitude, longitude));
    }

    public String trafficInfoKey(BigDecimal latitude, BigDecimal longitude) {
        return OPERATION + "?" + params(latitude, longitude).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public ItsResponse parse(String body) {
        ItsResponse response;
        try {
            response = objectMapper.readValue(body, ItsResponse.class);
        } catch (Exception e) {
            throw new ExternalApiException(ApiProvider.ITS_TRAFFIC,
                    "국가교통정보센터 응답을 해석하지 못했습니다.", e);
        }

        if (response.header() == null || !response.header().isSuccess()) {
            throw new ExternalApiException(ApiProvider.ITS_TRAFFIC,
                    "국가교통정보센터가 오류를 반환했습니다.");
        }

        return response;
    }

    /** 관광지 좌표를 중심으로 한 사각형. 설정한 반경만큼 사방으로 넓힌다. */
    private Map<String, String> params(BigDecimal latitude, BigDecimal longitude) {
        BigDecimal half = properties.halfSpan();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("type", "all");
        params.put("drcType", "all");
        params.put("minX", longitude.subtract(half).toPlainString());
        params.put("maxX", longitude.add(half).toPlainString());
        params.put("minY", latitude.subtract(half).toPlainString());
        params.put("maxY", latitude.add(half).toPlainString());
        params.put("getType", "json");
        return params;
    }

    private String fetchJson(Map<String, String> params) {
        if (!properties.hasApiKey()) {
            throw new ExternalApiException(ApiProvider.ITS_TRAFFIC,
                    "국가교통정보센터 인증키가 설정되지 않았습니다.");
        }

        String query = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        URI uri = URI.create(properties.baseUrl() + "/" + OPERATION
                + "?apiKey=" + properties.apiKey() + "&" + query);

        String body;
        try {
            body = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new ExternalApiException(ApiProvider.ITS_TRAFFIC,
                    "국가교통정보센터 호출에 실패했습니다.", e);
        }

        if (body == null || body.isBlank()) {
            throw new ExternalApiException(ApiProvider.ITS_TRAFFIC,
                    "국가교통정보센터 응답이 비어 있습니다.");
        }

        return body;
    }

    private static ClientHttpRequestFactory requestFactory(ItsProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());

        return factory;
    }
}
