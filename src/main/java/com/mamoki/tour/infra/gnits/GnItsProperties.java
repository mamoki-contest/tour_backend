package com.mamoki.tour.infra.gnits;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 강릉시 교통정보 조회서비스 접속 설정.
 *
 * <p>인증키는 KorService2 계열과 같은 공공데이터포털 Encoding 키({@code KOR_SERVICE_KEY})다.
 * 계정당 키가 하나이기 때문이다. 다만 <b>기관코드가 달라(4201000 vs B551011) base-url 은
 * 따로 둔다.</b> 국가교통정보센터(ITS, {@code tour.api.its})와도 다른 공급자다.
 *
 * @param rowsPerPage 한 번에 받을 행 수. 주차장이 13곳뿐이라 100 이면 1회 호출로 끝난다.
 *                    <b>페이지 루프를 돌면 안 된다.</b> pageNo 가 범위를 넘으면 빈 목록이
 *                    아니라 전체를 다시 돌려주어 종료 조건이 영원히 오지 않는다.
 * @param throttleRetries 본문이 {@code {}} 로 오는 조용한 스로틀을 만났을 때 다시 물어볼 횟수.
 * @param throttleBackoff 다시 묻기 전 기다릴 시간. 실측상 8초 이상 간격이면 스로틀이 사라진다.
 */
@ConfigurationProperties(prefix = "tour.api.gn-its")
public record GnItsProperties(
        String baseUrl,
        String serviceKey,
        Integer rowsPerPage,
        Integer throttleRetries,
        Duration throttleBackoff,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final int DEFAULT_ROWS_PER_PAGE = 100;
    private static final int DEFAULT_THROTTLE_RETRIES = 2;

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    public int rowsPerPageOrDefault() {
        return rowsPerPage == null || rowsPerPage <= 0 ? DEFAULT_ROWS_PER_PAGE : rowsPerPage;
    }

    public int throttleRetriesOrDefault() {
        return throttleRetries == null || throttleRetries < 0
                ? DEFAULT_THROTTLE_RETRIES : throttleRetries;
    }

    public Duration throttleBackoffOrDefault() {
        return throttleBackoff == null ? Duration.ofMillis(800) : throttleBackoff;
    }
}
