package com.mamoki.tour.global.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 허용 출처.
 *
 * <p>PRD 상 Vercel 배포 출처만 허용 목록에 둔다. 와일드카드를 쓰지 않는다.
 */
@ConfigurationProperties(prefix = "tour.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
