package com.mamoki.tour.global.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 기본값 없이 쓰이는 환경변수가 비어 있으면 바인딩 전에 기동을 멈춘다.
 *
 * <p>확인 규칙은 {@link RequiredEnvironmentVariables} 에 있고, 여기서는 그것을 언제
 * 부를지만 정한다. 빈 값을 지우는 {@link BlankValueEnvironmentPostProcessor} 가 무엇이
 * "비어 있는가" 를 정하므로 그 뒤에 와야 한다. 가장 마지막 순서로 두어 그 순서가
 * 등록 순서가 아니라 값으로 못 박히게 한다.
 */
public class RequiredEnvironmentVariablesPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        RequiredEnvironmentVariables.verify(environment);
    }
}
