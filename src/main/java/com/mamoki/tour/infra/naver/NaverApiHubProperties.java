package com.mamoki.tour.infra.naver;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 네이버 검색 API(NAVER API HUB) 접속 설정.
 *
 * <p>검색 API 는 개발자센터에서 NAVER API HUB 로 이관되었다. 도메인과 인증 헤더가 다르므로
 * 구 개발자센터 방식({@code openapi.naver.com}, {@code X-Naver-Client-*})으로는 인증되지 않는다.
 *
 * @param keyId     요청 헤더 {@code X-NCP-APIGW-API-KEY-ID}
 * @param key       요청 헤더 {@code X-NCP-APIGW-API-KEY}
 * @param callDelay 호출 간격. 키당 50 RPS 한도를 넘지 않도록 배치에서 사용한다.
 */
@ConfigurationProperties(prefix = "tour.api.naver")
public record NaverApiHubProperties(
        String baseUrl,
        String keyId,
        String key,
        Duration connectTimeout,
        Duration readTimeout,
        Duration callDelay
) {

    public boolean hasCredentials() {
        return keyId != null && !keyId.isBlank() && key != null && !key.isBlank();
    }
}
