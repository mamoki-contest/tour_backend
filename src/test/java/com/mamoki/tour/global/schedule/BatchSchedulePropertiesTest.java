package com.mamoki.tour.global.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import com.mamoki.tour.global.schedule.BatchScheduleProperties.Job;

/**
 * 스케줄 설정의 기본값.
 *
 * <p>{@code .env} 에서 키만 남기고 값을 비우는 일이 흔하다. 빈 값을 그대로 쓰면 cron 식이
 * 비어 기동이 깨지거나 시간대가 서버 기본값으로 밀린다. 주지 않은 것과 같게 본다.
 */
class BatchSchedulePropertiesTest {

    @Test
    @DisplayName("아무것도 주지 않으면 모두 꺼져 있다")
    void isDisabledByDefault() {
        BatchScheduleProperties properties = new BatchScheduleProperties(null, null, null);

        assertThat(properties.mentionEnabled()).isFalse();
        assertThat(properties.catalogEnabled()).isFalse();
    }

    @Test
    @DisplayName("시간대를 비우면 Asia/Seoul 로 본다")
    void fallsBackToSeoulZone() {
        assertThat(new BatchScheduleProperties(null, null, null).zoneId())
                .isEqualTo(ZoneId.of("Asia/Seoul"));
        assertThat(new BatchScheduleProperties("   ", null, null).zoneId())
                .isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("cron 을 비우면 매월 1일 기본 시각으로 본다")
    void fallsBackToMonthlyCron() {
        BatchScheduleProperties properties =
                new BatchScheduleProperties(null, new Job(true, " "), new Job(true, null));

        assertThat(properties.mentionCron()).isEqualTo("0 0 3 1 * *");
        assertThat(properties.catalogCron()).isEqualTo("0 0 2 1 * *");
    }

    /**
     * 언급량 수집이 카탈로그를 순회하므로, 둘 다 켜면 카탈로그가 먼저 끝나 있어야 한다.
     * 기본값을 고칠 때 이 순서가 뒤집히면 새로 들어온 관광지가 한 달 늦게 수집된다.
     */
    @Test
    @DisplayName("기본값은 매월 1일이고 카탈로그가 언급량보다 먼저 돈다")
    void importsCatalogBeforeCollectingMentions() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 12, 0);

        LocalDateTime catalog = CronExpression
                .parse(BatchScheduleProperties.DEFAULT_CATALOG_CRON).next(now);
        LocalDateTime mention = CronExpression
                .parse(BatchScheduleProperties.DEFAULT_MENTION_CRON).next(now);

        assertThat(catalog).isEqualTo(LocalDateTime.of(2026, 10, 1, 2, 0));
        assertThat(mention).isEqualTo(LocalDateTime.of(2026, 10, 1, 3, 0));
        assertThat(catalog).isBefore(mention);
    }

    @Test
    @DisplayName("준 값을 그대로 쓴다")
    void usesGivenValues() {
        BatchScheduleProperties properties = new BatchScheduleProperties(
                "UTC", new Job(true, "0 30 4 2 * *"), new Job(false, "0 0 1 * * *"));

        assertThat(properties.zoneId()).isEqualTo(ZoneId.of("UTC"));
        assertThat(properties.mentionCron()).isEqualTo("0 30 4 2 * *");
        assertThat(properties.catalogCron()).isEqualTo("0 0 1 * * *");
        assertThat(properties.mentionEnabled()).isTrue();
        assertThat(properties.catalogEnabled()).isFalse();
    }
}
