package com.mamoki.tour.global.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.time.YearMonth;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.mention.repository.OnlineMentionSnapshotRepository;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollectResult;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollector;
import com.mamoki.tour.global.enums.SnapshotStatus;

/**
 * 온라인 언급량 월간 수집 스케줄.
 *
 * <p>스케줄 실행은 {@code --job=mention} 과 같은 수집을 부른다. 여기서 검증하는 것은
 * 수집 자체가 아니라 <b>언제 부르지 않는가</b>다. 진행 중인 수집이 있으면 부르지 않고,
 * 수집이 실패해도 예외를 스케줄러 밖으로 흘리지 않는다.
 */
class MentionCollectScheduleTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private OnlineMentionCollector collector;
    private OnlineMentionSnapshotRepository snapshotRepository;
    private MentionCollectSchedule schedule;

    @BeforeEach
    void setUp() {
        collector = Mockito.mock(OnlineMentionCollector.class);
        snapshotRepository = Mockito.mock(OnlineMentionSnapshotRepository.class);

        given(collector.collect(any()))
                .willReturn(Mockito.mock(OnlineMentionCollectResult.class, Mockito.RETURNS_DEEP_STUBS));
        given(snapshotRepository.existsByStatus(SnapshotStatus.IMPORTING)).willReturn(false);

        schedule = new MentionCollectSchedule(collector, snapshotRepository, ZONE);
    }

    @Test
    @DisplayName("설정한 시간대 기준 이번 달로 수집한다")
    void collectsCurrentMonthInConfiguredZone() {
        schedule.collect();

        Mockito.verify(collector).collect(YearMonth.now(ZONE));
    }

    @Test
    @DisplayName("진행 중인 수집이 있으면 수집하지 않는다")
    void skipsWhileAnotherCollectionIsRunning() {
        given(snapshotRepository.existsByStatus(SnapshotStatus.IMPORTING)).willReturn(true);

        schedule.collect();

        Mockito.verifyNoInteractions(collector);
    }

    @Test
    @DisplayName("건너뛴 사실을 이력에 남긴다")
    void recordsSkippedRun() {
        given(snapshotRepository.existsByStatus(SnapshotStatus.IMPORTING)).willReturn(true);

        schedule.collect();

        assertThat(schedule.lastRun()).hasValueSatisfying(run ->
                assertThat(run.outcome()).isEqualTo(ScheduledRun.Outcome.SKIPPED));
    }

    @Test
    @DisplayName("수집이 실패해도 예외를 밖으로 던지지 않는다")
    void doesNotPropagateCollectionFailure() {
        willThrow(new IllegalStateException("공급자 장애")).given(collector).collect(any());

        assertThatCode(() -> schedule.collect()).doesNotThrowAnyException();

        assertThat(schedule.lastRun()).hasValueSatisfying(run -> {
            assertThat(run.outcome()).isEqualTo(ScheduledRun.Outcome.FAILED);
            assertThat(run.detail()).contains("공급자 장애");
        });
    }

    @Test
    @DisplayName("수집을 마치면 이력에 남긴다")
    void recordsCompletedRun() {
        schedule.collect();

        assertThat(schedule.lastRun()).hasValueSatisfying(run ->
                assertThat(run.outcome()).isEqualTo(ScheduledRun.Outcome.COMPLETED));
    }

    @Test
    @DisplayName("한 번도 돌지 않았으면 이력이 비어 있다")
    void hasNoRunBeforeFirstExecution() {
        assertThat(schedule.lastRun()).isEmpty();
    }
}
