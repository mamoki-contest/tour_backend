package com.mamoki.tour.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * {@code .env.example} 을 그대로 복사한 환경에서 기동에 필요한 값이 다 있는지 본다.
 *
 * <p>그 파일 첫 줄은 "그대로 복사해도 앱은 뜹니다" 라고 약속한다. 그런데 같은 파일의
 * {@code DB_USERNAME=} 이 비어 있던 동안 그 약속은 지켜지지 않았고, 깨지는 방식이
 * 고약했다 — 빈 값은 "지정하지 않은 것" 이므로 플레이스홀더가 해석되지 않은 채
 * {@code ${DB_USERNAME}} 이라는 글자가 MySQL 까지 내려가, 설정 오류가 <b>계정 이름이
 * {@code ${DB_USERNAME}} 인 인증 실패</b>로 위장됐다.
 *
 * <p>운영체제 환경변수를 떼고 본다({@link ConfigEnvironments}). 확인하려는 것이
 * "이 파일만으로 되는가" 라서, 테스트를 돌리는 사람의 셸에 {@code DB_USERNAME} 이
 * 있다는 이유로 통과하면 아무것도 확인하지 못한 것이 된다.
 *
 * <p>여기서 보는 것은 "필요한 값이 다 있는가" 까지다. 그 값으로 실제 MySQL 에 붙는 것은
 * 사람마다 다른 DB 의 문제이므로, 컨텍스트가 실제로 뜨는지는 {@code .env.example} 만
 * 복사한 상태에서 전체 테스트를 한 번 돌려 확인한다.
 */
class EnvExampleStartupTest {

    private static final Path ENV_EXAMPLE = Path.of(".env.example");

    @Test
    @DisplayName(".env.example 만 있으면 local 프로파일 기동에 필요한 값이 다 있다")
    void envExampleIsEnoughForLocalProfile() {
        assertThatCode(() -> processed(envExample(), "local")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName(".env.example 만 있으면 test 프로파일 기동에 필요한 값이 다 있다")
    void envExampleIsEnoughForTestProfile() {
        assertThatCode(() -> processed(envExample(), "test")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DB 계정을 비우면 그 변수 이름을 가리키며 멈춘다")
    void blankAccountNamesTheVariable() {
        MissingEnvironmentVariablesException failure =
                catchMissing(() -> processed(withBlank("DB_USERNAME"), "local"));

        assertThat(failure.names()).containsExactly("DB_USERNAME");
        assertThat(failure).hasMessageContaining("DB_USERNAME");
    }

    @Test
    @DisplayName("프로파일이 실제로 쓰는 스키마 변수를 가리킨다")
    void namesTheSchemaVariableOfTheActiveProfile() {
        assertThat(catchMissing(() -> processed(withBlank("TEST_DB_NAME"), "test")).names())
                .containsExactly("TEST_DB_NAME");

        assertThat(catchMissing(() -> processed(withBlank("DB_NAME"), "local")).names())
                .containsExactly("DB_NAME");
    }

    @Test
    @DisplayName("비어 있던 변수 이름을 사람이 읽을 안내에 그대로 적는다")
    void failureAnalyzerNamesTheVariable() {
        MissingEnvironmentVariablesException failure =
                catchMissing(() -> processed(withBlank("DB_USERNAME"), "local"));

        var analysis = new MissingEnvironmentVariablesFailureAnalyzer().analyze(failure);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("DB_USERNAME");
        assertThat(analysis.getAction()).contains(".env");
    }

    /** {@code .env.example} 을 앱이 {@code .env} 를 읽는 방식 그대로 읽는다. */
    private static Map<String, Object> envExample() {
        assertThat(ENV_EXAMPLE)
                .withFailMessage("저장소 루트에서 %s 를 찾지 못했다", ENV_EXAMPLE)
                .exists();

        return new LinkedHashMap<>(ConfigEnvironments.readEnvFile(ENV_EXAMPLE));
    }

    private static Map<String, Object> withBlank(String name) {
        Map<String, Object> values = envExample();
        values.put(name, "");

        return values;
    }

    /** 앱이 기동 초기에 하는 일과 같다 — 빈 값을 지우고 필수 값을 확인한다. */
    private static ConfigurableEnvironment processed(Map<String, Object> values, String profile) {
        ConfigurableEnvironment environment = ConfigEnvironments.isolated(profile);
        ConfigEnvironments.addValues(environment, "env-file", values);

        new BlankValueEnvironmentPostProcessor().postProcessEnvironment(environment, null);
        new RequiredEnvironmentVariablesPostProcessor().postProcessEnvironment(environment, null);

        return environment;
    }

    private static MissingEnvironmentVariablesException catchMissing(Runnable action) {
        try {
            action.run();
        } catch (MissingEnvironmentVariablesException expected) {
            return expected;
        }

        throw new AssertionError("필수 환경변수가 비었는데도 멈추지 않았다");
    }
}
