package com.mamoki.tour.global.config;

/**
 * 테스트가 접속해도 되는 스키마 이름인지 가린다.
 *
 * <p>테스트 프로파일은 {@code ddl-auto: create-drop} 이라 대상 스키마의 테이블을 매번
 * 지운다. 그래서 개발용 {@code tour} 나 운영 스키마를 가리키면 안 된다.
 *
 * <p>이름이 {@code tour_test} 하나로 고정되면 여러 갈래를 동시에 돌릴 수 없다. 갈래마다
 * {@code tour_test_62} 처럼 접미어를 붙여 스키마를 나누므로 접두어로 가린다.
 */
final class TestSchemaNames {

    private static final String TEST_SCHEMA_PREFIX = "tour_test";

    private TestSchemaNames() {
    }

    static boolean isTestSchema(String schema) {
        return schema != null && schema.startsWith(TEST_SCHEMA_PREFIX);
    }

    static String prefix() {
        return TEST_SCHEMA_PREFIX;
    }
}
