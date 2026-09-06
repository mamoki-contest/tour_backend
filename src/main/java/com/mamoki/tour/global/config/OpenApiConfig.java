package com.mamoki.tour.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tourOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("한사나다 API")
                .version("v1")
                .description("""
                        강원 관광 탐색 웹 백엔드 API.

                        ## 공통 응답 봉투
                        모든 응답은 `RsData` 봉투를 사용합니다.
                        `resultCode` 는 `{HTTP 상태}-{일련번호}` 형식이며, 앞자리에서 `statusCode` 를 파생합니다.
                        `statusCode` 는 HTTP 응답 상태와 항상 일치합니다.

                        ## 결측 처리
                        값이 없는 항목은 `0` 이나 빈 문자열이 아니라 `null` 로 내려갑니다.
                        `null` 을 `정보 없음`으로 표시하고 낮은 값으로 해석하지 마세요.

                        ## 공급자 장애
                        외부 공급자 장애는 오류가 아니라 정상 응답의 한 상태입니다.
                        데이터를 얻지 못해도 HTTP 200 으로 응답하며, `dataStatus` 로 구분합니다.

                        - `AVAILABLE` — 유효 기간 안의 정상 데이터
                        - `STALE` — 갱신에 실패해 최종 정상 데이터로 응답. `collectedAt` 이 오래된 시각입니다.
                        - `NO_DATA` — 정보 없음. 빈 목록으로 위장하지 않습니다.
                        """));
    }
}
