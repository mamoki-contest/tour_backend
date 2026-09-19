package com.mamoki.tour.global.config;

/**
 * 테스트가 접속해도 되는 스키마 이름인지 가린다.
 *
 * <p>테스트 프로파일은 {@code ddl-auto: create-drop} 이라 대상 스키마의 테이블을 매번
 * 지운다. 그래서 개발용 {@code tour} 나 운영 스키마를 가리키면 안 된다.
 *
 * <p>이름이 {@code tour_test} 하나로 고정되면 여러 갈래를 동시에 돌릴 수 없다. 갈래마다
 * {@code tour_test_62} 처럼 접미어를 붙여 스키마를 나누므로 구분자 {@code _} 까지 붙여서
 * 가린다. 접두어만 보면 {@code tour_testing_prod} 처럼 이름이 우연히 같게 시작하는
 * 운영 스키마가 통과해 테이블이 통째로 지워진다.
 */
final class TestSchemaNames {

    private static final String TEST_SCHEMA_NAME = "tour_test";

    private static final String TEST_SCHEMA_PREFIX = TEST_SCHEMA_NAME + "_";

    private TestSchemaNames() {
    }

    static boolean isTestSchema(String schema) {
        if (schema == null) {
            return false;
        }

        return schema.equals(TEST_SCHEMA_NAME) || schema.startsWith(TEST_SCHEMA_PREFIX);
    }

    /** 실패 메시지에 쓸 규칙 설명. */
    static String rule() {
        return "%s 이거나 %s 로 시작하는 이름".formatted(TEST_SCHEMA_NAME, TEST_SCHEMA_PREFIX);
    }
}
