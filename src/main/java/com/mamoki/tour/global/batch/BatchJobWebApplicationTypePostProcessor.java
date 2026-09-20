package com.mamoki.tour.global.batch;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * {@code --job=...} 으로 띄운 프로세스에서는 웹 서버를 열지 않는다(#95).
 *
 * <p>작업 하나를 돌리려고 띄운 프로세스다. 거기서 8080 을 물면 같은 서버에서 도는 운영
 * 프로세스와 포트가 부딪히고, 작업이 끝나도 서버 스레드가 살아 있어 프로세스가 내려가지
 * 않는다. 런북의 적재 명령은 {@code docker compose run --rm} 이라 끝나야 컨테이너가
 * 지워지는데, 예전에는 사람이 로그를 보고 {@code Ctrl+C} 로 빠져나오는 수밖에 없었다.
 *
 * <p>실행 형태는 컨텍스트를 만들기 전에 정해지므로 빈이 아니라 환경 후처리기로 둔다.
 * 값은 가장 낮은 우선순위로 얹는다. {@code --spring.main.web-application-type} 을 직접
 * 준 사람이 있으면 그쪽이 이긴다 — 이 판단은 편의이지 규칙이 아니다.
 */
public class BatchJobWebApplicationTypePostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "batchJobWebApplicationType";

    private static final String WEB_APPLICATION_TYPE = "spring.main.web-application-type";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {

        if (!BatchJobMode.isActive(environment)) {
            return;
        }

        environment.getPropertySources().addLast(new MapPropertySource(
                PROPERTY_SOURCE_NAME, Map.of(WEB_APPLICATION_TYPE, "none")));
    }
}
