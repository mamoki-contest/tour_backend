package com.mamoki.tour.global.batch;

import java.util.function.IntConsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 배치 프로세스를 작업 결과와 함께 내린다(#95).
 *
 * <p>{@code --job} 으로 띄운 프로세스는 작업이 끝나면 할 일이 없다. 그대로 두면 배포
 * 스크립트나 cron 이 끝나기를 기다리며 멈추고, {@code docker compose run --rm} 컨테이너도
 * 지워지지 않는다.
 *
 * <p>종료 코드는 {@link BatchJobRunner} 가 남긴 작업 결과다(성공 0 / 실패 1 / 알 수 없는
 * 작업 2). 스크립트는 로그를 읽지 않고 이 값으로 다음 단계를 정한다.
 *
 * <p>{@code --job} 이 없는 평범한 기동에서는 아무것도 하지 않는다. 그쪽은 서버다.
 */
public final class BatchJobExit {

    private BatchJobExit() {
    }

    /**
     * 배치 실행이면 컨텍스트를 닫고 종료 코드를 넘긴다.
     *
     * @param exit 종료를 실제로 수행할 곳. 운영에서는 {@code System::exit} 이며,
     *             테스트는 여기에 무엇이 넘어오는지만 본다.
     */
    public static void exitIfBatchJob(ConfigurableApplicationContext context, IntConsumer exit) {
        if (!BatchJobMode.isActive(context.getEnvironment())) {
            return;
        }

        exit.accept(SpringApplication.exit(context));
    }
}
