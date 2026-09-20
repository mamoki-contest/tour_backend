package com.mamoki.tour.global.schedule;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import com.mamoki.tour.global.batch.BatchJobMode;

/**
 * {@code --job=...} 으로 띄운 프로세스가 아닐 때만 통과한다.
 *
 * <p>배치 실행은 작업 하나를 돌리려고 띄운 프로세스다. 거기에 스케줄러까지 뜨면 손으로 돌린
 * 수집과 스케줄이 같은 프로세스 안에서 겹친다. 스케줄러는 평소 서버 기동에서만 뜬다.
 *
 * <p>무엇이 배치 실행인지는 {@link BatchJobMode} 한 곳이 정한다. 웹 서버를 열지 않는
 * 판단도, 작업 후 프로세스를 내리는 판단도 같은 자리를 보므로 셋이 어긋나지 않는다.
 */
class NotBatchJobMode implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !BatchJobMode.isActive(context.getEnvironment());
    }
}
