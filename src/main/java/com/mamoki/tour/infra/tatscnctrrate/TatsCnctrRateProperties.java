package com.mamoki.tour.infra.tatscnctrrate;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TatsCnctrRateService 접속 설정.
 *
 * <p>인증키는 KorService2·LocgoHub 와 같은 공공데이터포털 Encoding 키 하나를 공유한다.
 * 서비스별로는 활용신청으로 권한만 붙는다.
 *
 * @param rowsPerPage 한 번에 받아올 행 수. 이 공급자는 한 번 호출에 시·군 안의
 *                    (관광지 × 향후 30일) 을 모두 행으로 내려주므로 목록형 API 보다 행 수가 훨씬 많다.
 *                    관광지 40곳이면 1,200행이라 기본값을 크게 잡는다.
 * @param maxPages    한 시·군당 최대 페이지 수. 공급자가 예상보다 많은 행을 내려줄 때
 *                    호출이 무한히 늘어나지 않도록 막는 상한이다.
 */
@ConfigurationProperties(prefix = "tour.api.tats-cnctr-rate")
public record TatsCnctrRateProperties(
        String baseUrl,
        String serviceKey,
        String mobileApp,
        Integer rowsPerPage,
        Integer maxPages,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final int DEFAULT_ROWS_PER_PAGE = 1_000;
    private static final int DEFAULT_MAX_PAGES = 5;

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    public int rowsPerPageOrDefault() {
        return rowsPerPage == null || rowsPerPage < 1 ? DEFAULT_ROWS_PER_PAGE : rowsPerPage;
    }

    public int maxPagesOrDefault() {
        return maxPages == null || maxPages < 1 ? DEFAULT_MAX_PAGES : maxPages;
    }
}
