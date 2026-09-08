package com.mamoki.tour.infra.tatscnctrrate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import com.mamoki.tour.infra.tatscnctrrate.dto.TatsCnctrRateResponse;

/**
 * 관광지 집중률 방문자 추이 예측 조회 클라이언트.
 *
 * <p>인증키는 KorService2 와 같은 공공데이터포털 Encoding 키다. 추가 인코딩 없이 그대로 붙인다.
 *
 * <p>조회 단위가 관광지 단건이 아니라 <b>시·군</b> 이다. 한 번 호출하면 그 시·군 안의
 * 관광지마다 향후 30일치 행이 함께 온다. 목록 한 페이지가 관광지 20~100건이어도
 * 시·군 수만큼만 호출하면 되므로, 관광지마다 1회씩 부르지 않는다.
 *
 * <p>지역 지정에 KorService2 의 areaCode/sigunguCode 가 아니라 법정동 코드를 쓴다.
 * 두 체계의 변환은 region_code 테이블이 담당한다. LocgoHub 와 같은 방식이다.
 *
 * <p>공공데이터포털은 {@code _type=json} 이어도 게이트웨이 오류를 XML 로 내려준다.
 * 그 경우 파싱이 실패하고 {@link ExternalApiException} 으로 변환된다.
 */
@Component
public class TatsCnctrRateClient {

    private static final String OPERATION = "tatsCnctrRatedList";

    private final TatsCnctrRateProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TatsCnctrRateClient(TatsCnctrRateProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build();
    }

    /**
     * @param lawdCode 법정동 시·군 코드 5자리 (예: 51150)
     * @param pageNo   1부터 시작하는 페이지 번호
     */
    public String tatsCnctrRatedListJson(String lawdCode, int pageNo) {
        return fetchJson(params(lawdCode, pageNo));
    }

    /** 캐시 키. 인증키와 호출 앱 이름은 응답 내용을 바꾸지 않으므로 제외한다. */
    public String tatsCnctrRatedListKey(String lawdCode, int pageNo) {
        return OPERATION + "?" + params(lawdCode, pageNo).entrySet().stream()
                .filter(entry -> !"MobileApp".equals(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public int rowsPerPage() {
        return properties.rowsPerPageOrDefault();
    }

    public int maxPages() {
        return properties.maxPagesOrDefault();
    }

    public TatsCnctrRateResponse parse(String body) {
        TatsCnctrRateResponse response;
        try {
            response = objectMapper.readValue(body, TatsCnctrRateResponse.class);
        } catch (Exception e) {
            throw new ExternalApiException(ApiProvider.TATS_CNCTR_RATE,
                    "TatsCnctrRateService 응답을 해석하지 못했습니다.", e);
        }

        if (response.response() == null || response.response().header() == null
                || !response.response().header().isSuccess()) {
            throw new ExternalApiException(ApiProvider.TATS_CNCTR_RATE,
                    "TatsCnctrRateService 가 오류를 반환했습니다.");
        }

        return response;
    }

    /** 법정동 시·군 코드는 시·도 코드 2자리와 시·군 코드 5자리로 나누어 전달한다. */
    private Map<String, String> params(String lawdCode, int pageNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("MobileOS", "ETC");
        params.put("MobileApp", properties.mobileApp());
        params.put("_type", "json");
        params.put("numOfRows", String.valueOf(properties.rowsPerPageOrDefault()));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("areaCd", lawdCode.substring(0, 2));
        params.put("signguCd", lawdCode);
        return params;
    }

    private String fetchJson(Map<String, String> params) {
        if (!properties.hasServiceKey()) {
            throw new ExternalApiException(ApiProvider.TATS_CNCTR_RATE,
                    "TatsCnctrRateService 인증키가 설정되지 않았습니다.");
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
            throw new ExternalApiException(ApiProvider.TATS_CNCTR_RATE,
                    "TatsCnctrRateService 호출에 실패했습니다.", e);
        }

        if (body == null || body.isBlank()) {
            throw new ExternalApiException(ApiProvider.TATS_CNCTR_RATE,
                    "TatsCnctrRateService 응답이 비어 있습니다.");
        }

        return body;
    }

    private static ClientHttpRequestFactory requestFactory(TatsCnctrRateProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());

        return factory;
    }
}
