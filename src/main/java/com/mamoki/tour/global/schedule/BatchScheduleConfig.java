package com.mamoki.tour.global.schedule;

import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.support.CronTrigger;

import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportService;
import com.mamoki.tour.domain.mention.repository.OnlineMentionSnapshotRepository;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollector;

/**
 * 적재·수집 스케줄러를 등록한다.
 *
 * <p>두 조건을 모두 만족할 때만 스케줄러가 뜬다.
 *
 * <ol>
 *   <li><b>스케줄이 하나라도 켜져 있어야 한다.</b> 기본은 모두 꺼짐이다. 배포만으로 켜지면
 *       예고 없이 외부 API 호출이 나간다(#41 에서 스케줄러를 미룬 까닭이다).</li>
 *   <li><b>{@code --job=...} 배치 실행이 아니어야 한다.</b> 배치 프로세스는 작업 하나를
 *       돌리려고 띄운 것이라 스케줄러가 낄 자리가 없다.</li>
 * </ol>
 *
 * <p>cron 은 {@code @Scheduled} 애너테이션이 아니라 여기서 등록한다. 애너테이션에 두면
 * 기본값을 애너테이션과 {@code application.yaml} 양쪽에 적게 되고, 한쪽만 바꿔도 아무 일도
 * 일어나지 않는다. 기본값은 {@link BatchScheduleProperties} 상수 하나뿐이다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(BatchScheduleProperties.class)
@Conditional({NotBatchJobMode.class, AnyBatchScheduleEnabled.class})
public class BatchScheduleConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduleConfig.class);

    @Bean
    @ConditionalOnProperty(name = "tour.batch.schedule.mention.enabled", havingValue = "true")
    MentionCollectSchedule mentionCollectSchedule(OnlineMentionCollector collector,
                                                  OnlineMentionSnapshotRepository snapshotRepository,
                                                  BatchScheduleProperties properties) {
        return new MentionCollectSchedule(collector, snapshotRepository, properties.zoneId());
    }

    @Bean
    @ConditionalOnProperty(name = "tour.batch.schedule.catalog.enabled", havingValue = "true")
    CatalogImportSchedule catalogImportSchedule(AttractionCatalogImportService importService,
                                                BatchScheduleProperties properties) {
        return new CatalogImportSchedule(importService, properties.zoneId());
    }

    @Bean
    SchedulingConfigurer batchScheduleRegistrar(ObjectProvider<MentionCollectSchedule> mention,
                                                ObjectProvider<CatalogImportSchedule> catalog,
                                                BatchScheduleProperties properties) {
        ZoneId zone = properties.zoneId();

        return registrar -> {
            // 카탈로그가 언급량 수집의 바탕이라 순서가 어긋나면 안 된다. 기본 cron 이 두 시간
            // 차이를 두지만, 설정으로 바꿀 수 있으므로 무엇이 언제 도는지 기동 로그에 남긴다.
            catalog.ifAvailable(schedule -> {
                registrar.addCronTask(cronTask(schedule::importCatalog, properties.catalogCron(), zone));
                log.info("관광지 카탈로그 재적재 스케줄을 등록했습니다: cron={}, zone={}",
                        properties.catalogCron(), zone);
            });

            mention.ifAvailable(schedule -> {
                registrar.addCronTask(cronTask(schedule::collect, properties.mentionCron(), zone));
                log.info("온라인 언급량 수집 스케줄을 등록했습니다: cron={}, zone={}",
                        properties.mentionCron(), zone);
            });
        };
    }

    private static CronTask cronTask(Runnable runnable, String cron, ZoneId zone) {
        return new CronTask(runnable, new CronTrigger(cron, zone));
    }
}
