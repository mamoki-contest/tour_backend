package com.mamoki.tour.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * {@code CORS_ALLOWED_ORIGINS} 를 비우면 허용 출처가 비는지 본다.
 *
 * <p>이 앱에서 빈 값은 "지정하지 않은 것" 이다({@link BlankValueEnvironmentPostProcessor}).
 * 그래서 {@code application.yaml} 에 기본값을 두면 환경변수를 비우는 것이 "기본값을
 * 쓰겠다" 가 되어 버린다. 기본값이 {@code http://localhost:5173} 이던 동안 운영 서버가
 * 개발자 PC 주소를 허용해 돌려준 것이 그 결과다. {@code DB_PASSWORD} 처럼 이 항목만
 * 기본값을 비워 예외로 둔다.
 *
 * <p>실제 {@code application.yaml} 을 읽되 운영체제 환경변수는 떼고 본다. 테스트를 돌리는
 * 사람의 {@code .env} 나 셸에 값이 있으면 확인하려는 것이 가려지기 때문이다.
 */
class CorsAllowedOriginsDefaultTest {

    private static final String PROPERTY = "tour.cors.allowed-origins";

    @Test
    @DisplayName("허용 출처를 지정하지 않으면 빈 목록이 된다")
    void unsetOriginsBindToEmptyList() {
        ConfigurableEnvironment environment = applicationEnvironment();

        assertThat(environment.getProperty(PROPERTY)).isEmpty();
        assertThat(bind(environment).allowedOrigins()).isEmpty();
    }

    @Test
    @DisplayName("허용 출처를 빈 값으로 두어도 localhost 기본값이 되살아나지 않는다")
    void blankOriginsDoNotFallBackToLocalhost() {
        ConfigurableEnvironment environment = applicationEnvironment();
        ConfigEnvironments.addValues(environment, "env-file", Map.of("CORS_ALLOWED_ORIGINS", ""));
        new BlankValueEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty(PROPERTY)).isEmpty();
        assertThat(bind(environment).allowedOrigins()).isEmpty();
    }

    @Test
    @DisplayName("채운 출처는 쉼표로 갈라 그대로 쓴다")
    void filledOriginsAreUsedAsIs() {
        ConfigurableEnvironment environment = applicationEnvironment();
        ConfigEnvironments.addValues(environment, "env-file",
                Map.of("CORS_ALLOWED_ORIGINS", "https://tour.example,https://tour.vercel.app"));

        assertThat(bind(environment).allowedOrigins())
                .containsExactly("https://tour.example", "https://tour.vercel.app");
    }

    private static ConfigurableEnvironment applicationEnvironment() {
        ConfigurableEnvironment environment = ConfigEnvironments.isolated();
        ConfigEnvironments.addYaml(environment, "application.yaml");

        return environment;
    }

    private static CorsProperties bind(ConfigurableEnvironment environment) {
        return Binder.get(environment)
                .bind("tour.cors", CorsProperties.class)
                .orElseGet(() -> new CorsProperties(null));
    }
}
