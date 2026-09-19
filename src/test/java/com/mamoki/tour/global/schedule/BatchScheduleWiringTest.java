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
 * <p><b>cron 은 절대 오지 않는 날짜로 덮어쓴다.</b> 기본 cron 은 매월 1일 새벽이라,
 * 이 테스트를 매월 1일 02~03시에 돌리면 진짜 수집기와 적재기가 실제로 깨어나 외부 API
 * 호출을 내보낼 수 있다. 테스트가 우연히 외부 호출을 일으키는 일은 없어야 한다.
 * 2월 30일·31일은 어느 해에도 오지 않으므로 등록은 되지만 영원히 실행되지 않는다.
 *
 * <p>기본 cron 값 자체는 여기서 보지 않는다. 그쪽은 가짜 작업 빈만 쓰는
 * {@code BatchScheduleConfigTest} 와 {@code BatchSchedulePropertiesTest} 가 본다.
 */
@SpringBootTest(properties = {
        "tour.batch.schedule.mention.enabled=true",
        "tour.batch.schedule.catalog.enabled=true",
        "tour.batch.schedule.mention.cron=" + BatchScheduleWiringTest.NEVER_MENTION_CRON,
        "tour.batch.schedule.catalog.cron=" + BatchScheduleWiringTest.NEVER_CATALOG_CRON
})
@ActiveProfiles("test")
class BatchScheduleWiringTest {

    /** 2월 30일 03:00. 오지 않는 날짜다. */
    static final String NEVER_MENTION_CRON = "0 0 3 30 2 *";

    /** 2월 31일 02:00. 오지 않는 날짜다. */
    static final String NEVER_CATALOG_CRON = "0 0 2 31 2 *";

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
                .containsExactlyInAnyOrder(NEVER_MENTION_CRON, NEVER_CATALOG_CRON);
    }

    @Test
    @DisplayName("기동만으로는 아무 작업도 돌지 않는다")
    void runsNothingOnStartup() {
        assertThat(mentionCollectSchedule.lastRun()).isEmpty();
        assertThat(catalogImportSchedule.lastRun()).isEmpty();
    }
}
