package com.mamoki.tour.infra.locgohub;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LocgoHubTarService1 접속 설정.
 *
 * @param baseYm 조회 기준 월(yyyyMM). 비우면 공급자 제공 지연을 감안해 두 달 전을 사용한다.
 *               데이터랩 월 데이터는 지연 공개되므로 당월·전월은 비어 있을 수 있다.
 */
@ConfigurationProperties(prefix = "tour.api.locgo-hub")
public record LocgoHubProperties(
        String baseUrl,
        String serviceKey,
        String mobileApp,
        String baseYm,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int PROVIDER_LAG_MONTHS = 2;

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    public String resolveBaseYm(LocalDate today) {
        if (baseYm != null && !baseYm.isBlank()) {
            return baseYm;
        }

        return today.minusMonths(PROVIDER_LAG_MONTHS).format(YEAR_MONTH);
    }
}
