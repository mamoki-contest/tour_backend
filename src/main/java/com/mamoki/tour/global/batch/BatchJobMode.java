package com.mamoki.tour.global.batch;

import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * {@code --job=...} 으로 띄운 프로세스인지 가린다.
 *
 * <p>이 물음의 답은 세 곳이 쓴다. 스케줄러는 이때 뜨지 않고(#53), 웹 서버도 열지 않으며,
 * 작업이 끝나면 프로세스가 종료 코드와 함께 내려간다(#95). 세 곳이 각자 인자를 읽으면
 * 판단이 어긋나 어중간한 프로세스가 생기므로 규칙을 여기 한 곳에 둔다.
 *
 * <p>커맨드라인 인자만 본다. 환경변수나 설정 파일의 {@code job} 값은 배치 실행이 아니다.
 * 배치 실행은 사람이나 스크립트가 <b>그 순간 내린 명령</b>이지 환경의 성질이 아니다.
 */
public final class BatchJobMode {

    /**
     * 무엇을 돌릴지 고르는 인자 이름.
     *
     * <p>이 이름을 바꾸면 배치 실행 여부를 보는 모든 곳이 함께 바뀐다.
     */
    public static final String JOB_OPTION = "job";

    private BatchJobMode() {
    }

    /** 커맨드라인에 뜻 있는 {@code --job} 값이 실려 있으면 배치 실행이다. */
    public static boolean isActive(Environment environment) {
        if (!(environment instanceof ConfigurableEnvironment configurable)) {
            return false;
        }

        PropertySource<?> commandLine = configurable.getPropertySources()
                .get(CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME);

        if (commandLine == null) {
            return false;
        }

        Object value = commandLine.getProperty(JOB_OPTION);

        // 빈 값은 주지 않은 것으로 본다. BatchJobRunner 도 그렇게 보고 아무 작업도 돌리지 않는다.
        return value != null && !value.toString().isBlank();
    }
}
