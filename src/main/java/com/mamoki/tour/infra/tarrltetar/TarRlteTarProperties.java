package com.mamoki.tour.infra.tarrltetar;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TarRlteTarService1 접속 설정.
 *
 * <p>인증키는 KorService2·LocgoHub·TatsCnctrRate 와 같은 공공데이터포털 Encoding 키를 공유한다.
 * 서비스별로는 활용신청으로 권한만 붙는다.
 *
 * @param baseYm      조회 기준 월(yyyyMM). 비우면 공급자 제공 지연을 감안해 두 달 전을 사용한다.
 *                    LocgoHub 와 같은 데이터랩 계열이라 당월·전월은 비어 있을 수 있다.
 * @param rowsPerPage 한 번에 받아올 행 수. 한 행이 (기준 관광지 1곳 × 연관 장소 1곳) 이라
 *                    시·군 하나에 수천 행이 온다. 강릉시가 2,726행이라 기본값을 크게 잡는다.
 * @param maxPages    한 시·군당 최대 페이지 수. 공급자가 예상보다 많은 행을 내려줄 때
 *                    호출이 무한히 늘어나지 않도록 막는 상한이다.
 */
@ConfigurationProperties(prefix = "tour.api.tar-rlte-tar")
public record TarRlteTarProperties(
        String baseUrl,
        String serviceKey,
        String mobileApp,
        String baseYm,
        Integer rowsPerPage,
        Integer maxPages,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int PROVIDER_LAG_MONTHS = 2;

    private static final int DEFAULT_ROWS_PER_PAGE = 1_000;
    private static final int DEFAULT_MAX_PAGES = 5;

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    public String resolveBaseYm(LocalDate today) {
        if (baseYm != null && !baseYm.isBlank()) {
            return baseYm;
        }

        return today.minusMonths(PROVIDER_LAG_MONTHS).format(YEAR_MONTH);
    }

    public int rowsPerPageOrDefault() {
        return rowsPerPage == null || rowsPerPage < 1 ? DEFAULT_ROWS_PER_PAGE : rowsPerPage;
    }

    public int maxPagesOrDefault() {
        return maxPages == null || maxPages < 1 ? DEFAULT_MAX_PAGES : maxPages;
    }
}
