package com.mamoki.tour.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

/**
 * 허용 출처 목록이 비면 CORS 매핑을 등록하지 않는지 본다.
 *
 * <p>"빈 목록 = CORS 끔" 은 {@link WebConfig} 에 있었지만, {@code application.yaml} 의
 * 기본값이 {@code http://localhost:5173} 이던 동안에는 목록이 빌 수 없어 도달하지 못하는
 * 분기였다. 기본값을 비운 뒤 이 분기가 실제로 쓰이므로 여기서 못 박는다.
 */
class WebConfigCorsTest {

    @Test
    @DisplayName("허용 출처가 비면 매핑을 등록하지 않는다")
    void registersNothingWhenOriginsEmpty() {
        assertThat(register(List.of())).isEmpty();
    }

    @Test
    @DisplayName("허용 출처가 아예 없어도 매핑을 등록하지 않는다")
    void registersNothingWhenOriginsMissing() {
        assertThat(register(null)).isEmpty();
    }

    @Test
    @DisplayName("허용 출처를 채우면 그 출처로만 매핑을 등록한다")
    void registersConfiguredOrigins() {
        Map<String, CorsConfiguration> mappings = register(List.of("https://tour.example"));

        assertThat(mappings).containsOnlyKeys("/api/**");
        assertThat(mappings.get("/api/**").getAllowedOrigins())
                .containsExactly("https://tour.example");
    }

    private static Map<String, CorsConfiguration> register(List<String> allowedOrigins) {
        RecordingCorsRegistry registry = new RecordingCorsRegistry();

        new WebConfig(new CorsProperties(allowedOrigins)).addCorsMappings(registry);

        return registry.registered();
    }

    /** 등록된 매핑은 보호 수준이라 밖에서 볼 수 없다. 하위 클래스로 열어 둔다. */
    private static final class RecordingCorsRegistry extends CorsRegistry {

        private Map<String, CorsConfiguration> registered() {
            return getCorsConfigurations();
        }
    }
}
