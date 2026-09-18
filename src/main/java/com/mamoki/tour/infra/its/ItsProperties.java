package com.mamoki.tour.infra.its;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 국가교통정보센터(ITS) 접속 설정.
 *
 * <p>공공데이터포털이 아니라 openapi.its.go.kr 에서 따로 발급한 키를 쓴다.
 * {@code KOR_SERVICE_KEY} 와 무관하다.
 *
 * @param halfSpan 관광지 좌표를 중심으로 몇 도까지 볼지. 0.005 도가 약 555m 다.
 *                 응답에 링크 좌표가 없어 가까운 도로를 고를 수 없으므로 범위로 좁힌다.
 *                 넓히면 관광지와 상관없는 간선도로가 섞이고, 좁히면 시골에서 빈 응답이 된다.
 */
@ConfigurationProperties(prefix = "tour.api.its")
public record ItsProperties(
        String baseUrl,
        String apiKey,
        BigDecimal halfSpan,
        Duration connectTimeout,
        Duration readTimeout
) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
