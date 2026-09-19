package com.mamoki.tour.global.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.core.env.Environment;

/**
 * 기본값 없이 쓰이는 환경변수가 채워져 있는지 바인딩 전에 확인한다.
 *
 * <p>대상은 {@code application.yaml} 계열이 {@code ${VAR}} 로, 즉 기본값 없이 참조하는
 * 값들이다. DB 접속 정보가 전부 그렇다 — 계정 이름도 호스트도 비어 있으면 뜻이 없어서
 * 기본값을 둘 자리가 없다.
 *
 * <p>비어 있을 때 그냥 두면 <b>설정 오류가 설정 오류로 보이지 않는다.</b> 바인더는
 * 해석되지 않은 플레이스홀더를 그대로 흘려보내므로 {@code ${DB_USERNAME}} 이라는 글자가
 * MySQL 까지 내려가고, 사람은 "그런 계정으로 인증에 실패했다" 는 메시지를 받는다. 값이
 * 쓰이는 자리까지 가기 전에, 어느 <b>변수</b>가 비었는지 이름으로 말하고 멈춘다.
 *
 * <p>프로파일마다 가리키는 스키마가 달라 마지막 한 항목만 갈린다. {@code test} 는
 * {@code TEST_DB_NAME} 을, 나머지는 {@code DB_NAME} 을 쓴다.
 */
final class RequiredEnvironmentVariables {

    private static final String TEST_PROFILE = "test";

    /** 프로파일과 무관하게 필요한 값. */
    private static final List<String> ALWAYS_REQUIRED = List.of("DB_HOST", "DB_PORT", "DB_USERNAME");

    private RequiredEnvironmentVariables() {
    }

    /** 비어 있는 필수 환경변수 이름. 다 채워져 있으면 빈 목록. */
    static List<String> missing(Environment environment) {
        List<String> required = new ArrayList<>(ALWAYS_REQUIRED);
        required.add(schemaVariable(environment));

        return required.stream()
                .filter(name -> !isSet(environment, name))
                .toList();
    }

    static void verify(Environment environment) {
        List<String> missing = missing(environment);

        if (!missing.isEmpty()) {
            throw new MissingEnvironmentVariablesException(missing);
        }
    }

    private static String schemaVariable(Environment environment) {
        return usesTestProfile(environment) ? "TEST_DB_NAME" : "DB_NAME";
    }

    private static boolean usesTestProfile(Environment environment) {
        String[] active = environment.getActiveProfiles();
        String[] profiles = active.length > 0 ? active : environment.getDefaultProfiles();

        return Arrays.asList(profiles).contains(TEST_PROFILE);
    }

    /**
     * 빈 값은 이미 지워졌지만({@link BlankValueEnvironmentPostProcessor}) 여기서도 공백을
     * 본다. 이 검사만 따로 불러 쓰는 자리에서도 같은 답이 나와야 한다.
     */
    private static boolean isSet(Environment environment, String name) {
        String value = environment.getProperty(name);

        return value != null && !value.isBlank();
    }
}
