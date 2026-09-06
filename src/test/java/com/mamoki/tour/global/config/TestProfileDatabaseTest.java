package com.mamoki.tour.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 테스트 프로파일은 ddl-auto: create-drop 이므로 대상 스키마의 테이블을 삭제한다.
 * 개발용 스키마나 배포 서버 DB 를 가리키지 않는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class TestProfileDatabaseTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("테스트는 개발용이 아닌 테스트 전용 스키마에 접속한다")
    void connectsToTestSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String schema = connection.getCatalog();

            assertThat(schema).isEqualTo("tour_test");
        }
    }
}
