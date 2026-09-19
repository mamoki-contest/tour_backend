package com.mamoki.tour.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 관통 테스트가 다른 테스트와 스키마를 나눠 쓰지 않게 한다(#86).
 *
 * <p>관통 테스트는 스프링 컨텍스트를 하나 더 만든다. 테스트 프로파일은 {@code ddl-auto:
 * create-drop} 이라 컨텍스트가 뜰 때마다 대상 스키마의 테이블을 통째로 지우고 다시 만든다.
 * 지금은 컨텍스트가 순서대로 뜨고 스프링 컨텍스트 캐시(기본 32개)에 전부 남아 있어 겉으로는
 * 안전하지만, 캐시에서 축출되어 다시 뜨거나 병렬로 실행되면 <b>다른 테스트가 쓰고 있는 스키마를
 * 드롭한다</b>. 그때 깨지는 것은 이 관통 테스트가 아니라 아무 상관 없는 테스트다.
 *
 * <p>그래서 관통 테스트마다 접미어로 스키마를 나눈다({@code tour_test_85} →
 * {@code tour_test_85_through}). 없으면 만든다 — 사람이 스키마를 미리 만들어 두는 일을 잊는 것이
 * 다시 조용한 실패가 되기 때문이다. 만들 권한이 없으면 건너뛰지 않고 실패한다.
 *
 * <p>{@code @DynamicPropertySource} 가 아니라 초기화자인 이유는 <b>이미 해석된</b> 접속 URL 이
 * 필요해서다. URL 은 {@code application-test.yaml} 에서 조립되는데 그 값이 실제 환경변수로 올
 * 수도 있고 {@code .env} 파일로 올 수도 있다. 여기서 URL 을 다시 조립하면 양쪽을 모두 흉내 내야
 * 하고 형식이 한쪽만 바뀌어도 알 수 없다. 초기화자는 환경이 다 준비된 뒤에 돌므로 해석된 URL 을
 * 받아 스키마 이름만 갈아 끼우면 된다.
 */
abstract class ThroughTestSchemaInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /**
     * 테스트 전용 스키마 접두어. 여기서 만드는 이름도 반드시 이것으로 시작해야 한다.
     *
     * <p>{@code TestSchemaNames} 가 같은 값을 갖고 있지만 그쪽은 다른 패키지의 package-private
     * 이다. 스키마를 새로 <b>만드는</b> 쪽에서 한 번 더 확인하는 값이라 여기 따로 둔다.
     */
    private static final String TEST_SCHEMA_PREFIX = "tour_test";

    private static final String JDBC_MYSQL = "jdbc:mysql://";

    private final String suffix;

    protected ThroughTestSchemaInitializer(String suffix) {
        this.suffix = suffix;
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();

        String sharedUrl = required(environment, "spring.datasource.url");
        String username = required(environment, "spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password", "");

        if (!sharedUrl.startsWith(JDBC_MYSQL)) {
            throw new IllegalStateException(
                    "MySQL 접속 URL 이 아니라 관통 테스트를 격리할 수 없습니다: " + sharedUrl);
        }

        int schemaStart = sharedUrl.indexOf('/', JDBC_MYSQL.length()) + 1;
        if (schemaStart == 0) {
            throw new IllegalStateException(
                    "접속 URL 에서 스키마 자리를 찾지 못했습니다: " + sharedUrl);
        }

        int queryStart = sharedUrl.indexOf('?', schemaStart);
        String server = sharedUrl.substring(0, schemaStart);
        String parameters = queryStart < 0 ? "" : sharedUrl.substring(queryStart);
        String schema = (queryStart < 0
                ? sharedUrl.substring(schemaStart)
                : sharedUrl.substring(schemaStart, queryStart)) + suffix;

        if (!schema.startsWith(TEST_SCHEMA_PREFIX)) {
            throw new IllegalStateException(
                    "관통 테스트 스키마는 %s 로 시작해야 합니다. 지금 만들려는 곳: %s"
                            .formatted(TEST_SCHEMA_PREFIX, schema));
        }

        createSchemaIfAbsent(server + parameters, username, password, schema);

        TestPropertyValues.of("spring.datasource.url=" + server + schema + parameters)
                .applyTo(context);
    }

    private static String required(ConfigurableEnvironment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "%s 가 비어 있어 관통 테스트 스키마를 준비할 수 없습니다.".formatted(key));
        }

        return value;
    }

    /**
     * 스키마가 없으면 만든다. 권한이 없으면 건너뛰지 않고 실패한다 — 관통 테스트가 조용히
     * 공유 스키마로 돌아가면 이 격리가 있으나 마나이기 때문이다.
     */
    private static void createSchemaIfAbsent(String serverUrl, String username, String password,
                                             String schema) {

        try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(
                    "CREATE DATABASE IF NOT EXISTS `%s` DEFAULT CHARACTER SET utf8mb4".formatted(schema));

        } catch (SQLException e) {
            throw new IllegalStateException(
                    ("관통 테스트 전용 스키마 %s 를 만들지 못했습니다. 접속 계정에 스키마 생성 권한이 "
                            + "있어야 합니다(테스트는 root 를 전제합니다). 직접 만들려면: "
                            + "CREATE DATABASE %s DEFAULT CHARACTER SET utf8mb4;")
                            .formatted(schema, schema), e);
        }
    }
}
