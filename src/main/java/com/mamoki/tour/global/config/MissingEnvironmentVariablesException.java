package com.mamoki.tour.global.config;

import java.util.List;

/**
 * 기본값이 없는 필수 환경변수가 비어 있어 기동을 멈춘다.
 *
 * <p>설정 오류가 <b>설정 오류로 보이게</b> 하려고 둔다. 이것이 없으면 해석되지 않은
 * {@code ${DB_USERNAME}} 이 그대로 내려가 MySQL 이 그 글자를 계정 이름으로 받고,
 * 사람은 "Access denied for user '${DB_USERNAME}'" 라는 인증 실패를 보게 된다.
 */
public class MissingEnvironmentVariablesException extends RuntimeException {

    private final List<String> names;

    MissingEnvironmentVariablesException(List<String> names) {
        super(message(names));
        this.names = List.copyOf(names);
    }

    /** 비어 있던 환경변수 이름. */
    public List<String> names() {
        return names;
    }

    private static String message(List<String> names) {
        return "필수 환경변수가 비어 있거나 지정되지 않았습니다: " + String.join(", ", names);
    }
}
