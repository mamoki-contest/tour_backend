package com.mamoki.tour.global.schedule;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;

import com.mamoki.tour.global.batch.BatchJobRunner;

/**
 * {@code --job=...} 으로 띄운 프로세스가 아닐 때만 통과한다.
 *
 * <p>배치 실행은 작업 하나를 돌리려고 띄운 프로세스다. 거기에 스케줄러까지 뜨면 손으로 돌린
 * 수집과 스케줄이 같은 프로세스 안에서 겹친다. 스케줄러는 평소 서버 기동에서만 뜬다.
 *
 * <p>커맨드라인 인자만 본다. 환경변수나 설정 파일의 {@code job} 값은 배치 실행이 아니다.
 * {@link BatchJobRunner} 도 같은 자리에서 같은 이름을 읽으므로 둘의 판단이 어긋나지 않는다.
 */
class NotBatchJobMode implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !isBatchJobMode(context.getEnvironment());
    }

    static boolean isBatchJobMode(Environment environment) {
        if (!(environment instanceof ConfigurableEnvironment configurable)) {
            return false;
        }

        PropertySource<?> commandLine = configurable.getPropertySources()
                .get(CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME);

        if (commandLine == null) {
            return false;
        }

        Object value = commandLine.getProperty(BatchJobRunner.JOB_OPTION);

        // 빈 값은 주지 않은 것으로 본다. BatchJobRunner 도 그렇게 보고 아무 작업도 돌리지 않는다.
        return value != null && !value.toString().isBlank();
    }
}
