package com.mamoki.tour.global.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스케줄러가 실제 애플리케이션 안에서 붙는지.
 *
 * <p>{@code BatchScheduleConfigTest} 는 가짜 작업 빈으로 조건만 본다. 여기서는 진짜
 * 수집기·적재기·리포지토리가 있는 컨텍스트에 붙여, 켰을 때 정말로 뜨는지 확인한다.
 * 조건은 맞는데 의존성이 없어 기동이 깨지는 일을 여기서 잡는다.
 *
 * <p>cron 이 매월 1일이라 테스트가 도는 동안 실제로 실행되지는 않는다.
 */
@SpringBootTest(properties = {
        "tour.batch.schedule.mention.enabled=true",
        "tour.batch.schedule.catalog.enabled=true"
})
@ActiveProfiles("test")
class BatchScheduleWiringTest {

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledTaskHolder;

    @Autowired
    private MentionCollectSchedule mentionCollectSchedule;

    @Autowired
    private CatalogImportSchedule catalogImportSchedule;

    @Test
    @DisplayName("켜면 두 스케줄이 cron 으로 등록된다")
    void registersBothSchedules() {
        Set<ScheduledTask> tasks = scheduledTaskHolder.getScheduledTasks();

        assertThat(tasks).hasSize(2);
        assertThat(tasks).allSatisfy(task -> assertThat(task.getTask()).isInstanceOf(CronTask.class));
        assertThat(tasks)
                .map(task -> ((CronTask) task.getTask()).getExpression())
                .containsExactlyInAnyOrder(
                        BatchScheduleProperties.DEFAULT_MENTION_CRON,
                        BatchScheduleProperties.DEFAULT_CATALOG_CRON);
    }

    @Test
    @DisplayName("기동만으로는 아무 작업도 돌지 않는다")
    void runsNothingOnStartup() {
        assertThat(mentionCollectSchedule.lastRun()).isEmpty();
        assertThat(catalogImportSchedule.lastRun()).isEmpty();
    }
}
