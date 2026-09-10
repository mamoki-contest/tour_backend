package com.mamoki.tour.infra.korservice;

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
import com.mamoki.tour.infra.korservice.dto.KorServiceResponse;

/**
 * KorService2 호출 클라이언트.
 *
 * <p>인증키는 공공데이터포털의 Encoding 키를 그대로 사용한다. 이미 URL 인코딩된 문자열이라
 * 한 번 더 인코딩하면 {@code %2F} 가 {@code %252F} 가 되어 인증에 실패한다. 그래서 질의
 * 문자열을 직접 만들고, serviceKey 만 인코딩하지 않고 붙인다.
 */
@Component
public class KorServiceClient {

    private final KorServiceProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public KorServiceClient(KorServiceProperties properties) {
        this.properties = properties;
        // 공급자 응답 전용 매퍼. 알 수 없는 필드가 늘어나도 깨지지 않도록 별도로 둔다.
        //
        // 결과가 없으면 items 를 객체가 아니라 빈 문자열("")로 내려준다. 이를 해석 실패로
        // 두면 0건이 정보 없음으로 둔갑해, 검색 결과가 없는 것과 공급자 장애를 구분할 수 없다.
        this.objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build();
    }

    /**
     * 지역 기반 관광지 목록을 조회한다.
     *
     * @param sigunguCode 관광공사 시·군구 코드. null 이면 지역 전체.
     */
    public KorServiceResponse areaBasedList(String areaCode, String sigunguCode,
                                            String contentTypeId, int pageNo, int numOfRows) {

        return parse("areaBasedList2",
                fetchJson("areaBasedList2",
                        areaBasedListParams(areaCode, sigunguCode, contentTypeId, pageNo, numOfRows)));
    }

    /** 지역 코드 목록을 조회한다. areaCode 가 null 이면 시·도 목록, 있으면 그 안의 시·군 목록. */
    public KorServiceResponse areaCode(String areaCode, int numOfRows) {
        return parse("areaCode2", fetchJson("areaCode2", areaCodeParams(areaCode, numOfRows)));
    }

    /** 캐시 계층이 원본 응답을 그대로 보관할 수 있도록 JSON 문자열을 반환한다. */
    public String areaBasedListJson(String areaCode, String sigunguCode,
                                    String contentTypeId, int pageNo, int numOfRows) {

        return fetchJson("areaBasedList2",
                areaBasedListParams(areaCode, sigunguCode, contentTypeId, pageNo, numOfRows));
    }

    /** 캐시 키. 같은 조건이면 항상 같은 문자열이 나오도록 파라미터 순서를 고정한다. */
    public String areaBasedListKey(String areaCode, String sigunguCode,
                                   String contentTypeId, int pageNo, int numOfRows) {

        return requestKey("areaBasedList2",
                areaBasedListParams(areaCode, sigunguCode, contentTypeId, pageNo, numOfRows));
    }

    /**
     * 키워드로 관광지를 검색한다.
     *
     * @param contentTypeId 분류를 좁히고 싶을 때만 지정한다. null 이면 전체.
     */
    public String searchKeywordJson(String areaCode, String sigunguCode, String contentTypeId,
                                    String keyword, int pageNo, int numOfRows) {

        return fetchJson("searchKeyword2",
                searchKeywordParams(areaCode, sigunguCode, contentTypeId, keyword, pageNo, numOfRows));
    }

    public String searchKeywordKey(String areaCode, String sigunguCode, String contentTypeId,
                                   String keyword, int pageNo, int numOfRows) {

        return requestKey("searchKeyword2",
                searchKeywordParams(areaCode, sigunguCode, contentTypeId, keyword, pageNo, numOfRows));
    }

    private Map<String, String> searchKeywordParams(String areaCode, String sigunguCode,
                                                    String contentTypeId, String keyword,
                                                    int pageNo, int numOfRows) {
        Map<String, String> params = commonParams();
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("areaCode", areaCode);
        params.put("keyword", keyword);

        if (sigunguCode != null) {
            params.put("sigunguCode", sigunguCode);
        }
        if (contentTypeId != null) {
            params.put("contentTypeId", contentTypeId);
        }

        return params;
    }

    /**
     * 관광지 상세 기본정보를 조회한다.
     *
     * <p>목록과 같은 항목 구조에 전화번호·홈페이지·개요가 더 붙는다. 목록에는 없는 정보라
     * 상세를 열 때만 호출한다.
     */
    public String detailCommonJson(String contentId) {
        return fetchJson("detailCommon2", detailCommonParams(contentId));
    }

    public String detailCommonKey(String contentId) {
        return requestKey("detailCommon2", detailCommonParams(contentId));
    }

    private Map<String, String> detailCommonParams(String contentId) {
        Map<String, String> params = commonParams();
        params.put("contentId", contentId);

        return params;
    }

    private Map<String, String> areaBasedListParams(String areaCode, String sigunguCode,
                                                    String contentTypeId, int pageNo, int numOfRows) {
        Map<String, String> params = commonParams();
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("areaCode", areaCode);

        if (sigunguCode != null) {
            params.put("sigunguCode", sigunguCode);
        }
        if (contentTypeId != null) {
            params.put("contentTypeId", contentTypeId);
        }

        return params;
    }

    private Map<String, String> areaCodeParams(String areaCode, int numOfRows) {
        Map<String, String> params = commonParams();
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", "1");

        if (areaCode != null) {
            params.put("areaCode", areaCode);
        }

        return params;
    }

    private Map<String, String> commonParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("MobileOS", "ETC");
        params.put("MobileApp", properties.mobileApp());
        params.put("_type", "json");
        return params;
    }

    private static String requestKey(String operation, Map<String, String> params) {
        return operation + "?" + params.entrySet().stream()
                .filter(entry -> !"MobileApp".equals(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private String fetchJson(String operation, Map<String, String> params) {
        if (!properties.hasServiceKey()) {
            throw new ExternalApiException(ApiProvider.KOR_SERVICE2, "KorService2 인증키가 설정되지 않았습니다.");
        }

        URI uri = buildUri(operation, params);

        String body;
        try {
            body = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new ExternalApiException(ApiProvider.KOR_SERVICE2,
                    "KorService2 호출에 실패했습니다: " + operation, e);
        }

        if (body == null || body.isBlank()) {
            throw new ExternalApiException(ApiProvider.KOR_SERVICE2,
                    "KorService2 응답이 비어 있습니다: " + operation);
        }

        return body;
    }

    /**
     * 인증 실패 등 공급자 오류는 JSON 이 아닌 XML 로 내려오기도 한다.
     * 그 경우도 외부 호출 실패로 변환해 캐시·폴백 계층이 처리하게 한다.
     */
    public KorServiceResponse parse(String operation, String body) {
        KorServiceResponse response;
        try {
            response = objectMapper.readValue(body, KorServiceResponse.class);
        } catch (Exception e) {
            throw new ExternalApiException(ApiProvider.KOR_SERVICE2,
                    "KorService2 응답을 해석하지 못했습니다: " + operation, e);
        }

        if (response.response() == null || response.response().header() == null
                || !response.response().header().isSuccess()) {
            throw new ExternalApiException(ApiProvider.KOR_SERVICE2,
                    "KorService2 가 오류를 반환했습니다: " + operation);
        }

        return response;
    }

    /** serviceKey 는 이미 인코딩된 값이므로 그대로 붙이고, 나머지 파라미터만 인코딩한다. */
    private URI buildUri(String operation, Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));

        return URI.create(properties.baseUrl() + "/" + operation
                + "?serviceKey=" + properties.serviceKey() + "&" + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ClientHttpRequestFactory requestFactory(KorServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());

        return factory;
    }
}
