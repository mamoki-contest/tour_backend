package com.mamoki.tour.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.origin.SystemEnvironmentOrigin;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * 빈 값을 "지정하지 않은 것"으로 되돌리는 규칙을 직접 확인한다.
 *
 * <p>환경은 실제 시스템 프로퍼티·환경변수를 싣지 않은 빈 것을 쓴다. 테스트를 실행하는
 * 사람의 환경변수가 결과를 흔들지 않아야 한다.
 */
class BlankValueEnvironmentPostProcessorTest {

    private static final Origin FILE_ORIGIN = new Origin() {
    };

    private final BlankValueEnvironmentPostProcessor processor =
            new BlankValueEnvironmentPostProcessor();

    @Test
    @DisplayName(".env 에서 온 빈 값은 지워지고 채운 값은 남는다")
    void removesBlankValuesFromEnvFile() {
        ConfigurableEnvironment environment = emptyEnvironment();
        environment.getPropertySources().addFirst(envFileSource(Map.of(
                "DATA_LAB_LAG_DAYS", "",
                "KOR_SERVICE_BASE_URL", "   ",
                "DATA_LAB_WINDOW_DAYS", "5"
        )));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.containsProperty("DATA_LAB_LAG_DAYS")).isFalse();
        assertThat(environment.containsProperty("KOR_SERVICE_BASE_URL")).isFalse();
        assertThat(environment.getProperty("DATA_LAB_WINDOW_DAYS")).isEqualTo("5");
    }

    @Test
    @DisplayName("지운 뒤에도 남은 값의 출처 정보를 잃지 않는다")
    void keepsOriginOfRemainingValues() {
        ConfigurableEnvironment environment = emptyEnvironment();
        environment.getPropertySources().addFirst(envFileSource(Map.of(
                "DATA_LAB_LAG_DAYS", "",
                "DATA_LAB_WINDOW_DAYS", "5"
        )));

        processor.postProcessEnvironment(environment, null);

        PropertySource<?> replaced = environment.getPropertySources().get("env-file");
        assertThat(replaced).isInstanceOf(OriginTrackedMapPropertySource.class);
        assertThat(OriginLookup.getOrigin(replaced, "DATA_LAB_WINDOW_DAYS"))
                .isEqualTo(FILE_ORIGIN);
    }

    @Test
    @DisplayName("운영체제 환경변수의 빈 값도 똑같이 지운다")
    void removesBlankValuesFromSystemEnvironment() {
        ConfigurableEnvironment environment = emptyEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "systemEnvironment",
                Map.of(
                        "TOUR_API_ITS_HALF_SPAN", "",
                        "TOUR_API_ITS_BASE_URL", "http://openapi.its.go.kr"
                )
        ));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.containsProperty("tour.api.its.half-span")).isFalse();
        assertThat(environment.getProperty("tour.api.its.base-url"))
                .isEqualTo("http://openapi.its.go.kr");
    }

    @Test
    @DisplayName("환경변수 소스를 갈아 끼운 뒤에도 느슨한 이름 맞춤과 출처가 살아 있다")
    void keepsSystemEnvironmentBehaviour() {
        ConfigurableEnvironment environment = emptyEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "systemEnvironment",
                Map.of(
                        "TOUR_API_ITS_HALF_SPAN", "",
                        "TOUR_API_ITS_BASE_URL", "http://openapi.its.go.kr"
                )
        ));

        processor.postProcessEnvironment(environment, null);

        PropertySource<?> replaced = environment.getPropertySources().get("systemEnvironment");
        assertThat(replaced).isInstanceOf(SystemEnvironmentPropertySource.class);
        assertThat(OriginLookup.getOrigin(replaced, "tour.api.its.base-url"))
                .isEqualTo(new SystemEnvironmentOrigin("TOUR_API_ITS_BASE_URL"));
    }

    @Test
    @DisplayName("빈 목록은 값이 없다는 뜻이 아니므로 건드리지 않는다")
    void keepsNonTextValues() {
        ConfigurableEnvironment environment = emptyEnvironment();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tour.cors.allowed-origins", List.of());
        environment.getPropertySources().addFirst(new MapPropertySource("programmatic", values));

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.containsProperty("tour.cors.allowed-origins")).isTrue();
    }

    @Test
    @DisplayName("지울 것이 없는 소스는 그대로 둔다")
    void leavesUntouchedSourceAlone() {
        ConfigurableEnvironment environment = emptyEnvironment();
        PropertySource<?> original = envFileSource(Map.of("DATA_LAB_WINDOW_DAYS", "5"));
        environment.getPropertySources().addFirst(original);

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().get("env-file")).isSameAs(original);
    }

    /** 실제 시스템 프로퍼티·환경변수를 싣지 않는 환경. */
    private static ConfigurableEnvironment emptyEnvironment() {
        return new AbstractEnvironment() {
        };
    }

    /** {@code .env} 를 읽었을 때 만들어지는 것과 같은 모양의 소스. */
    private static PropertySource<?> envFileSource(Map<String, String> values) {
        Map<String, Object> tracked = new LinkedHashMap<>();
        values.forEach((name, value) -> tracked.put(name, OriginTrackedValue.of(value, FILE_ORIGIN)));

        return new OriginTrackedMapPropertySource("env-file", tracked, true);
    }
}
