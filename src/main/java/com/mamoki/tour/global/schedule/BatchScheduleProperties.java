package com.mamoki.tour.global.schedule;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 적재·수집 스케줄 설정.
 *
 * <p><b>기본은 모두 꺼짐이다.</b> 켜는 순간 사람이 명령을 치지 않아도 외부 API 호출이
 * 나가므로, 배포만으로 켜지는 일이 없어야 한다.
 *
 * <p>기본 cron 과 시간대는 여기 상수가 유일한 출처다. {@code application.yaml} 은 환경변수를
 * 연결하기만 하고 값을 다시 적지 않는다. 두 곳에 적으면 한쪽만 바뀌어도 알 수 없다.
 *
 * @param zone    cron 을 해석할 시간대. 비우면 {@link #DEFAULT_ZONE}
 * @param mention 온라인 언급량 월간 수집
 * @param catalog 관광지 카탈로그 재적재
 */
@ConfigurationProperties("tour.batch.schedule")
public record BatchScheduleProperties(String zone, Job mention, Job catalog) {

    /** 서비스 대상이 강원이고 운영자도 국내에 있다. 서버 시간대와 무관하게 한국 시각으로 해석한다. */
    public static final String DEFAULT_ZONE = "Asia/Seoul";

    /** 매월 1일 03:00. 새벽이라 조회가 가장 적고, 카탈로그 재적재가 끝난 뒤다. */
    public static final String DEFAULT_MENTION_CRON = "0 0 3 1 * *";

    /** 매월 1일 02:00. 언급량 수집이 이 카탈로그를 순회하므로 반드시 먼저 끝나야 한다. */
    public static final String DEFAULT_CATALOG_CRON = "0 0 2 1 * *";

    /**
     * 켜고 끌 수 있는 스케줄 하나.
     *
     * @param enabled 켜짐 여부. 기본 꺼짐
     * @param cron    Spring cron 식(6자리). 비우면 각 작업의 기본값
     */
    public record Job(boolean enabled, String cron) {
    }

    public ZoneId zoneId() {
        return ZoneId.of(valueOr(zone, DEFAULT_ZONE));
    }

    public boolean mentionEnabled() {
        return mention != null && mention.enabled();
    }

    public String mentionCron() {
        return valueOr(mention == null ? null : mention.cron(), DEFAULT_MENTION_CRON);
    }

    public boolean catalogEnabled() {
        return catalog != null && catalog.enabled();
    }

    public String catalogCron() {
        return valueOr(catalog == null ? null : catalog.cron(), DEFAULT_CATALOG_CRON);
    }

    /** 환경변수를 비워 둔 것과 주지 않은 것을 같게 본다. {@code .env} 에서 키만 남기는 일이 흔하다. */
    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
