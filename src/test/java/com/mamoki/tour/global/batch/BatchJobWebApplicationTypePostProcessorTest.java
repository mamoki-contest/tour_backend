package com.mamoki.tour.global.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * {@code --job=...} 으로 띄운 프로세스는 웹 서버를 열지 않는다(#95).
 *
 * <p>작업 하나를 돌리려고 띄운 프로세스다. 거기서 8080 을 물면 운영 중인 서버와 포트가
 * 부딪히고, 작업이 끝나도 프로세스가 살아 있어 배포 스크립트나 cron 이 영원히 기다린다.
 *
 * <p>판단 근거는 커맨드라인 인자뿐이다. 스케줄러가 보는 조건과 같은 자리를 본다
 * ({@link BatchJobMode}). 두 곳이 다른 것을 보면 배치 프로세스에서 스케줄러가 함께 뜨거나,
 * 웹 서버만 빠진 채 스케줄이 도는 어중간한 프로세스가 생긴다.
 */
class BatchJobWebApplicationTypePostProcessorTest {

    private static final String WEB_APPLICATION_TYPE = "spring.main.web-application-type";

    private final BatchJobWebApplicationTypePostProcessor postProcessor =
            new BatchJobWebApplicationTypePostProcessor();

    private String webApplicationTypeAfter(String... args) {
        ConfigurableEnvironment environment = new StandardEnvironment();

        if (args.length > 0) {
            environment.getPropertySources().addFirst(new SimpleCommandLinePropertySource(args));
        }

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        return environment.getProperty(WEB_APPLICATION_TYPE);
    }

    @Test
    @DisplayName("--job 으로 띄우면 웹 서버를 열지 않는다")
    void batchJobRunsWithoutWebServer() {
        assertThat(webApplicationTypeAfter("--job=mention")).isEqualTo("none");
    }

    @Test
    @DisplayName("--job 이 없으면 평소대로 웹 서버로 뜬다")
    void staysAWebServerWithoutJobOption() {
        assertThat(webApplicationTypeAfter()).isNull();
        assertThat(webApplicationTypeAfter("--spring.profiles.active=prod", "--server.port=8080"))
                .isNull();
    }

    @Test
    @DisplayName("빈 job 값은 주지 않은 것으로 본다")
    void treatsBlankJobAsAbsent() {
        // BatchJobRunner 도 빈 값이면 아무 작업도 돌리지 않는다. 그 프로세스는 평범한 서버다.
        assertThat(webApplicationTypeAfter("--job=")).isNull();
    }

    @Test
    @DisplayName("알 수 없는 작업 이름이어도 웹 서버를 열지 않는다")
    void unknownJobStillRunsWithoutWebServer() {
        // 이름이 틀렸는지는 작업 실행기가 가린다. 여기서 다시 가리면 두 곳의 판단이 갈리고,
        // 오타 하나로 8080 을 문 프로세스가 남는다.
        assertThat(webApplicationTypeAfter("--job=드롭테이블")).isEqualTo("none");
    }

    @Test
    @DisplayName("직접 지정한 실행 형태가 있으면 그쪽을 따른다")
    void explicitSettingWins() {
        assertThat(webApplicationTypeAfter("--job=mention",
                "--" + WEB_APPLICATION_TYPE + "=servlet")).isEqualTo("servlet");
    }
}
