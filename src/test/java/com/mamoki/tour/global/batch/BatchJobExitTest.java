package com.mamoki.tour.global.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.SimpleCommandLinePropertySource;

/**
 * 배치 프로세스의 끝.
 *
 * <p>작업이 끝나면 <b>종료 코드와 함께 내려가야</b> 한다(#95). 런북의 적재 명령은
 * {@code docker compose run --rm} 이라 프로세스가 끝나야 컨테이너가 지워지고, 배포
 * 스크립트나 cron 은 종료 코드로 성공·실패를 읽는다. 예전에는 작업을 마쳐도 웹 서버로
 * 남아서, 사람이 로그를 보고 {@code Ctrl+C} 로 빠져나오는 수밖에 없었다.
 *
 * <p>{@code --job} 이 없는 평범한 기동은 여기서 아무것도 하지 않는다. 그쪽은 서버다.
 */
class BatchJobExitTest {

    @Test
    @DisplayName("--job 으로 띄운 프로세스는 작업 결과를 종료 코드로 남기고 내려간다")
    void exitsWithJobResult() {
        ConfigurableApplicationContext context = context(1, "--job=catalog");
        List<Integer> exitCodes = new ArrayList<>();

        BatchJobExit.exitIfBatchJob(context, exitCodes::add);

        assertThat(exitCodes).containsExactly(1);
        assertThat(context.isActive()).isFalse();
    }

    @Test
    @DisplayName("작업이 성공하면 종료 코드는 0 이다")
    void exitsWithZeroWhenJobSucceeded() {
        List<Integer> exitCodes = new ArrayList<>();

        BatchJobExit.exitIfBatchJob(context(0, "--job=catalog"), exitCodes::add);

        assertThat(exitCodes).containsExactly(0);
    }

    @Test
    @DisplayName("--job 이 없으면 내려가지 않고 서버로 남는다")
    void staysUpWithoutJobOption() {
        ConfigurableApplicationContext context = context(0);
        List<Integer> exitCodes = new ArrayList<>();

        BatchJobExit.exitIfBatchJob(context, exitCodes::add);

        assertThat(exitCodes).isEmpty();
        assertThat(context.isActive()).isTrue();

        context.close();
    }

    /** {@code --job} 인자와 작업 결과(종료 코드)를 가진 컨텍스트. */
    private static ConfigurableApplicationContext context(int exitCode, String... args) {
        GenericApplicationContext context = new GenericApplicationContext();

        if (args.length > 0) {
            context.getEnvironment().getPropertySources()
                    .addFirst(new SimpleCommandLinePropertySource(args));
        }

        context.registerBean("batchJobExitCode", ExitCodeGenerator.class,
                () -> () -> exitCode);
        context.refresh();

        return context;
    }
}
