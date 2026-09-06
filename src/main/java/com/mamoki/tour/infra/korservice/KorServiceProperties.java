package com.mamoki.tour.infra.korservice;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KorService2 접속 설정.
 *
 * @param baseUrl        공급자 기본 URL
 * @param serviceKey     공공데이터포털 인증키. Encoding 키를 그대로 사용한다.
 * @param mobileApp      공급자가 요구하는 호출 애플리케이션명
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout    응답 대기 제한 시간
 */
@ConfigurationProperties(prefix = "tour.api.kor-service")
public record KorServiceProperties(
        String baseUrl,
        String serviceKey,
        String mobileApp,
        Duration connectTimeout,
        Duration readTimeout
) {

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
