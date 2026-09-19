package com.mamoki.tour.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 스키마 가드가 어디까지 받아주는지 못 박는다. DB 없이 규칙만 확인한다.
 */
class TestSchemaNamesTest {

    @Test
    @DisplayName("갈래마다 접미어를 붙인 테스트 스키마를 받아준다")
    void acceptsSuffixedTestSchema() {
        assertThat(TestSchemaNames.isTestSchema("tour_test")).isTrue();
        assertThat(TestSchemaNames.isTestSchema("tour_test_51")).isTrue();
        assertThat(TestSchemaNames.isTestSchema("tour_test_review")).isTrue();
    }

    @Test
    @DisplayName("개발용·운영 스키마는 막는다")
    void rejectsNonTestSchema() {
        assertThat(TestSchemaNames.isTestSchema("tour")).isFalse();
        assertThat(TestSchemaNames.isTestSchema("tour_dev_51")).isFalse();
        assertThat(TestSchemaNames.isTestSchema("tour_prod")).isFalse();
        assertThat(TestSchemaNames.isTestSchema("mysql")).isFalse();
    }

    @Test
    @DisplayName("스키마를 알 수 없으면 막는다")
    void rejectsUnknownSchema() {
        assertThat(TestSchemaNames.isTestSchema(null)).isFalse();
        assertThat(TestSchemaNames.isTestSchema("")).isFalse();
    }
}
