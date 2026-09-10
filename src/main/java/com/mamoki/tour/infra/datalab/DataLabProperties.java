package com.mamoki.tour.infra.datalab;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DataLabService 접속 설정.
 *
 * <p>공급자가 최근 데이터를 바로 공개하지 않는다. 2026-09-10 확인 기준 2026-08-11 까지만
 * 제공되어 지연이 약 한 달이다. 지연 길이는 공급자 사정으로 바뀔 수 있어 설정으로 뺀다.
 *
 * @param lagDays    오늘로부터 며칠 전을 기준 기간의 마지막 날로 볼지. 이 값이 실제 지연보다
 *                   짧으면 빈 응답이 오고, 그때는 값을 만들어내지 않고 정보 없음으로 처리한다.
 * @param windowDays 기준 기간의 길이. 하루만 보면 요일 편차가 그대로 드러나므로 한 주를 묶는다.
 * @param endYmd     기준 기간의 마지막 날 고정값(yyyyMMdd). 비우면 lagDays 로 계산한다.
 */
@ConfigurationProperties(prefix = "tour.api.data-lab")
public record DataLabProperties(
        String baseUrl,
        String serviceKey,
        String mobileApp,
        String endYmd,
        int lagDays,
        int windowDays,
        int rowsPerPage,
        int maxPages,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    public LocalDate resolveEndDate(LocalDate today) {
        if (endYmd != null && !endYmd.isBlank()) {
            return LocalDate.parse(endYmd, YMD);
        }

        return today.minusDays(lagDays);
    }

    /** 기준 기간은 마지막 날을 포함해 windowDays 일이다. */
    public LocalDate resolveStartDate(LocalDate today) {
        return resolveEndDate(today).minusDays(windowDays - 1L);
    }

    public static String format(LocalDate date) {
        return date.format(YMD);
    }
}
