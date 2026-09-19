package com.mamoki.tour.global.schedule;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 스케줄이 하나라도 켜져 있을 때만 통과한다.
 *
 * <p>하나도 켜져 있지 않으면 {@code @EnableScheduling} 자체를 붙이지 않는다. 스케줄러
 * 스레드를 띄워 놓고 아무것도 돌리지 않을 이유가 없다.
 */
class AnyBatchScheduleEnabled extends AnyNestedCondition {

    AnyBatchScheduleEnabled() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(name = "tour.batch.schedule.mention.enabled", havingValue = "true")
    static class MentionEnabled {
    }

    @ConditionalOnProperty(name = "tour.batch.schedule.catalog.enabled", havingValue = "true")
    static class CatalogEnabled {
    }
}
