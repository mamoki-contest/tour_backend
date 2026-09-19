package com.mamoki.tour.infra.gnits;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.gnits.dto.GnItsEnvelope;
import com.mamoki.tour.infra.gnits.dto.GnParkInfoResponse;
import com.mamoki.tour.infra.gnits.dto.GnParkRltmResponse;

/**
 * 강릉시 교통정보 조회서비스(주차) 클라이언트.
 *
 * <p>강원 18개 시·군 중 실시간 주차 오픈API 를 여는 곳은 강릉시뿐이다. 두 오퍼레이션을
 * {@code prkId} 로 이어 쓴다.
 *
 * <ul>
 *   <li>{@code getParkInfo} — 이름·주소·좌표·운영시각. 거의 바뀌지 않아 24시간 캐시한다
 *   <li>{@code getParkRltm} — 전체 주차면과 점유 대수. 도로 소통과 같은 5분 캐시다
 * </ul>
 *
 * <p><b>조용한 스로틀링이 있다.</b> 짧은 간격으로 연속 호출하면 HTTP 200 에 본문이
 * {@code {}} 만 온다. 오류 응답이 아니라 헤더 자체가 없는 빈 객체다. 파싱 실패로 넘기면
 * 공급자 장애로 오인하므로, 본문을 해석하기 전에 먼저 가려내고 짧게 기다렸다 다시 묻는다.
 * 실측상 3초 간격에서 17%, 8초 이상 간격에서 0% 다. 빈 응답도 일일 쿼터를 소모한다.
 *
 * <p><b>페이지 루프를 돌지 않는다.</b> {@code pageNo} 가 범위를 넘으면 빈 목록이 아니라
 * 전체 13건을 다시 돌려주어, 빈 페이지를 종료 조건으로 삼은 루프가 끝나지 않는다.
 * 주차장이 13곳이므로 {@code numOfRows=100} 으로 1회만 부른다.
 *
 * <p>인증키는 공공데이터포털 Encoding 키라 추가 인코딩 없이 그대로 붙인다.
 */
@Component
public class GnItsClient {

    public static final String SOURCE = "강릉시 교통정보 조회서비스";

    static final String PARK_INFO = "getParkInfo";
    static final String PARK_RLTM = "getParkRltm";

    private static final Logger log = LoggerFactory.getLogger(GnItsClient.class);

    private final GnItsProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GnItsClient(
            GnItsProperties properties,
            @Qualifier("gnItsRestClientBuilder") RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.restClient = restClientBuilder.build();
    }

    public String parkInfoJson() {
        return fetchJson(PARK_INFO);
    }

    public String parkRltmJson() {
        return fetchJson(PARK_RLTM);
    }

    /** 캐시 키. 인증키는 응답 내용을 바꾸지 않으므로 담지 않는다. */
    public String parkInfoKey() {
        return requestKey(PARK_INFO);
    }

    public String parkRltmKey() {
        return requestKey(PARK_RLTM);
    }

    public GnParkInfoResponse parseParkInfo(String body) {
        return parse(body, GnParkInfoResponse.class, PARK_INFO);
    }

    public GnParkRltmResponse parseParkRltm(String body) {
        return parse(body, GnParkRltmResponse.class, PARK_RLTM);
    }

    /**
     * 스로틀 판정.
     *
     * <p><b>키가 하나도 없는 빈 JSON 객체일 때만</b> 스로틀로 본다. 게이트웨이 오류
     * ({@code {"OpenAPI_ServiceResponse":...}}) 도 우리가 기대하는 {@code header} 가 없지만
     * 그것은 다시 물어도 같은 답이 오는 진짜 오류다. "header 가 없으면 스로틀" 로 잡으면
     * 인증키가 잘못됐을 때 조용히 재시도만 반복하게 된다.
     */
    public boolean isThrottled(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }

        try {
            JsonNode node = objectMapper.readTree(body);
            return node.isObject() && node.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private <T extends GnItsEnvelope> T parse(String body, Class<T> type, String operation) {
        if (isThrottled(body)) {
            throw new ExternalApiException(ApiProvider.GN_ITS_PARKING,
                    "%s 가 빈 응답을 돌려주었습니다(호출 제한).".formatted(operation));
        }

        T response;
        try {
            response = objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new ExternalApiException(ApiProvider.GN_ITS_PARKING,
                    "강릉시 교통정보 조회서비스 응답을 해석하지 못했습니다.", e);
        }

        if (response.header() == null || !response.header().isSuccess()) {
            throw new ExternalApiException(ApiProvider.GN_ITS_PARKING,
                    "강릉시 교통정보 조회서비스가 오류를 반환했습니다.");
        }

        return response;
    }

    /** 요청 파라미터 세 개가 모두 필수다. 하나라도 빠지면 resultCode 11 이 온다. */
    private Map<String, String> params() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pageNo", "1");
        params.put("numOfRows", String.valueOf(properties.rowsPerPageOrDefault()));
        return params;
    }

    private String requestKey(String operation) {
        return operation + "?" + params().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private String fetchJson(String operation) {
        if (!properties.hasServiceKey()) {
            throw new ExternalApiException(ApiProvider.GN_ITS_PARKING,
                    "강릉시 교통정보 조회서비스 인증키가 설정되지 않았습니다.");
        }

        String query = params().entrySet().stream()
                .map(entry -> entry.getKey() + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        URI uri = URI.create(properties.baseUrl() + "/" + operation
                + "?serviceKey=" + properties.serviceKey() + "&" + query);

        return fetchWithThrottleRetry(operation, () -> get(uri, operation));
    }

    /**
     * 빈 응답을 만나면 짧게 기다렸다 다시 묻는다.
     *
     * <p>끝내 비어 있으면 예외로 올린다. 캐시 계층이 이를 받아 최종 정상 데이터나 정보 없음
     * 으로 바꾼다. 빈 목록으로 위장하지 않는다 — 그러면 "주차장이 없다"로 보인다.
     */
    String fetchWithThrottleRetry(String operation, Supplier<String> call) {
        int attempts = properties.throttleRetriesOrDefault() + 1;
        Duration backoff = properties.throttleBackoffOrDefault();

        for (int attempt = 1; attempt <= attempts; attempt++) {
            String body = call.get();

            if (!isThrottled(body)) {
                return body;
            }

            log.warn("{} 가 빈 응답을 돌려주었습니다(호출 제한). {}/{}회", operation, attempt, attempts);

            if (attempt < attempts) {
                sleep(backoff);
            }
        }

        throw new ExternalApiException(ApiProvider.GN_ITS_PARKING,
                "%s 가 %d회 연속 빈 응답을 돌려주었습니다(호출 제한).".formatted(operation, attempts));
    }

    private String get(URI uri, String operation) {
        String body;
        try {
            body = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new ExternalApiException(ApiProvider.GN_ITS_PARKING,
                    "강릉시 교통정보 조회서비스 호출에 실패했습니다: " + operation, e);
        }

        if (body == null || body.isBlank()) {
            throw new ExternalApiException(ApiProvider.GN_ITS_PARKING,
                    "강릉시 교통정보 조회서비스 응답이 비어 있습니다: " + operation);
        }

        return body;
    }

    private static void sleep(Duration backoff) {
        if (backoff.isZero() || backoff.isNegative()) {
            return;
        }

        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException(ApiProvider.GN_ITS_PARKING,
                    "강릉시 교통정보 조회서비스 재시도 대기가 중단되었습니다.", e);
        }
    }
}
