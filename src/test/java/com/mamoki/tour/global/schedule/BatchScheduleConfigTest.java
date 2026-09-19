package com.mamoki.tour.global.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.support.CronTrigger;

import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportService;
import com.mamoki.tour.domain.mention.repository.OnlineMentionSnapshotRepository;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollector;

/**
 * 스케줄러가 뜨는 조건.
 *
 * <p>여기서 가장 중요한 것은 <b>기본이 off</b> 라는 것과 <b>{@code --job} 배치 모드에서는
 * 뜨지 않는다</b>는 것이다. 둘 중 하나가 깨지면 배포하자마자 예고 없이 외부 API 호출이
 * 나가거나, 손으로 돌린 배치와 스케줄이 겹쳐 같은 키로 호출이 두 배가 된다.
 */
class BatchScheduleConfigTest {

    private static final String MENTION_CRON = "0 0 3 1 * *";
    private static final String CATALOG_CRON = "0 0 2 1 * *";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StubJobs.class, BatchScheduleConfig.class);

    /** {@code java -jar app.jar --job=mention} 으로 띄운 프로세스를 흉내 낸다. */
    private ApplicationContextRunner withCommandLine(String... args) {
        return runner.withInitializer(context -> context.getEnvironment().getPropertySources()
                .addFirst(new SimpleCommandLinePropertySource(args)));
    }

    private Set<ScheduledTask> scheduledTasks(AssertableApplicationContext context) {
        return context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
    }

    private CronTask onlyCronTask(AssertableApplicationContext context) {
        Set<ScheduledTask> tasks = scheduledTasks(context);

        assertThat(tasks).hasSize(1);

        return (CronTask) tasks.iterator().next().getTask();
    }

    @Test
    @DisplayName("아무 설정도 주지 않으면 스케줄러가 뜨지 않는다")
    void isOffByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(MentionCollectSchedule.class);
            assertThat(context).doesNotHaveBean(CatalogImportSchedule.class);
            assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }

    @Test
    @DisplayName("설정을 false 로 두어도 스케줄러가 뜨지 않는다")
    void staysOffWhenDisabled() {
        runner.withPropertyValues(
                "tour.batch.schedule.mention.enabled=false",
                "tour.batch.schedule.catalog.enabled=false"
        ).run(context -> {
            assertThat(context).doesNotHaveBean(MentionCollectSchedule.class);
            assertThat(context).doesNotHaveBean(CatalogImportSchedule.class);
        });
    }

    @Test
    @DisplayName("언급량 스케줄을 켜면 설정한 cron 과 시간대로 등록된다")
    void registersMentionScheduleWithConfiguredCron() {
        runner.withPropertyValues(
                "tour.batch.schedule.mention.enabled=true",
                "tour.batch.schedule.mention.cron=" + MENTION_CRON,
                "tour.batch.schedule.zone=Asia/Seoul"
        ).run(context -> {
            assertThat(context).hasSingleBean(MentionCollectSchedule.class);
            assertThat(onlyCronTask(context).getTrigger())
                    .isEqualTo(new CronTrigger(MENTION_CRON, ZoneId.of("Asia/Seoul")));
        });
    }

    @Test
    @DisplayName("언급량 스케줄을 켜도 카탈로그 스케줄은 꺼져 있다")
    void leavesCatalogOffWhenOnlyMentionIsEnabled() {
        runner.withPropertyValues("tour.batch.schedule.mention.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(CatalogImportSchedule.class));
    }

    @Test
    @DisplayName("카탈로그 스케줄을 켜면 설정한 cron 으로 등록된다")
    void registersCatalogScheduleWithConfiguredCron() {
        runner.withPropertyValues(
                "tour.batch.schedule.catalog.enabled=true",
                "tour.batch.schedule.catalog.cron=" + CATALOG_CRON
        ).run(context -> {
            assertThat(context).hasSingleBean(CatalogImportSchedule.class);
            assertThat(context).doesNotHaveBean(MentionCollectSchedule.class);
            assertThat(onlyCronTask(context).getExpression()).isEqualTo(CATALOG_CRON);
        });
    }

    @Test
    @DisplayName("시간대를 주지 않으면 Asia/Seoul 로 등록된다")
    void defaultsToSeoulZone() {
        runner.withPropertyValues(
                "tour.batch.schedule.mention.enabled=true",
                "tour.batch.schedule.mention.cron=" + MENTION_CRON
        ).run(context -> assertThat(onlyCronTask(context).getTrigger())
                .isEqualTo(new CronTrigger(MENTION_CRON, ZoneId.of("Asia/Seoul"))));
    }

    @Test
    @DisplayName("시간대를 바꾸면 그 시간대로 등록된다")
    void honoursConfiguredZone() {
        runner.withPropertyValues(
                "tour.batch.schedule.mention.enabled=true",
                "tour.batch.schedule.mention.cron=" + MENTION_CRON,
                "tour.batch.schedule.zone=UTC"
        ).run(context -> assertThat(onlyCronTask(context).getTrigger())
                .isEqualTo(new CronTrigger(MENTION_CRON, ZoneId.of("UTC"))));
    }

    @Test
    @DisplayName("--job 배치 모드에서는 설정을 켜 두어도 스케줄러가 뜨지 않는다")
    void staysOffInBatchJobMode() {
        withCommandLine("--job=mention")
                .withPropertyValues(
                        "tour.batch.schedule.mention.enabled=true",
                        "tour.batch.schedule.catalog.enabled=true"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MentionCollectSchedule.class);
                    assertThat(context).doesNotHaveBean(CatalogImportSchedule.class);
                    assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
                });
    }

    @Test
    @DisplayName("배치와 무관한 인자만 있으면 스케줄러는 평소대로 뜬다")
    void runsWithUnrelatedCommandLineArguments() {
        withCommandLine("--spring.profiles.active=prod", "--server.port=8080")
                .withPropertyValues("tour.batch.schedule.mention.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(MentionCollectSchedule.class));
    }

    @Test
    @DisplayName("빈 --job 값은 배치 모드가 아니다")
    void treatsBlankJobAsServerMode() {
        withCommandLine("--job=")
                .withPropertyValues("tour.batch.schedule.mention.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(MentionCollectSchedule.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class StubJobs {

        @Bean
        OnlineMentionCollector onlineMentionCollector() {
            return Mockito.mock(OnlineMentionCollector.class);
        }

        @Bean
        OnlineMentionSnapshotRepository onlineMentionSnapshotRepository() {
            return Mockito.mock(OnlineMentionSnapshotRepository.class);
        }

        @Bean
        AttractionCatalogImportService attractionCatalogImportService() {
            return Mockito.mock(AttractionCatalogImportService.class);
        }
    }
}
