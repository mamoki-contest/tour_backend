package com.mamoki.tour.global.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * 필수 환경변수가 비었을 때 스택 트레이스 대신 무엇을 채우면 되는지 보여준다.
 */
class MissingEnvironmentVariablesFailureAnalyzer
        extends AbstractFailureAnalyzer<MissingEnvironmentVariablesException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, MissingEnvironmentVariablesException cause) {
        String description = "다음 환경변수가 비어 있거나 지정되지 않아 기동을 멈췄습니다: %s"
                .formatted(String.join(", ", cause.names()));

        String action = """
                .env 파일이나 운영체제 환경변수에 값을 채우세요.

                이 앱은 값이 빈 줄을 "지정하지 않은 것" 으로 봅니다. 그래서 `DB_USERNAME=` 처럼
                두면 채운 것이 아니라 비운 것이 되고, DB 접속 정보에는 기본값이 없으므로
                (계정 이름이나 호스트는 비어 있으면 뜻이 없습니다) 기동이 여기서 멈춥니다.

                `.env.example` 을 그대로 복사하면 필요한 값이 이미 채워져 있습니다.
                DB 계정은 README 의 "로컬 개발 환경" 에 적힌 것과 같아야 합니다.
                테스트는 `TEST_DB_NAME`, 그 밖에는 `DB_NAME` 이 필요합니다.
                """;

        return new FailureAnalysis(description, action, cause);
    }
}
