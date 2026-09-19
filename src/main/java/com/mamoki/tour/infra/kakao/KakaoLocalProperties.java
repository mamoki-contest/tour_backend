package com.mamoki.tour.infra.kakao;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로컬 REST API 접속 설정.
 *
 * <p>로컬 API 는 REST API 키를 {@code Authorization: KakaoAK {key}} 헤더로 받는다.
 * JavaScript 키가 아니며 도메인 등록도 필요 없다. 서버 전용이라 프론트로 내려보내지 않는다.
 *
 * @param restApiKey       developers.kakao.com 의 REST API 키. 비어 있으면 배치가 즉시 끝난다.
 * @param callDelay        호출 간격. 로컬 검색 한도는 하루 100,000회이며, 이 간격이 한 번의
 *                         배치가 짧은 시간에 한도를 소진하지 않게 막는다.
 * @param size             한 번에 받을 장소 수. 카카오 상한은 15 다.
 * @param searchRadius     시·군 중심에서 훑을 반경(m). 카카오 상한은 20,000 이다.
 */
@ConfigurationProperties(prefix = "tour.api.kakao")
public record KakaoLocalProperties(
        String baseUrl,
        String restApiKey,
        Duration connectTimeout,
        Duration readTimeout,
        Duration callDelay,
        int size,
        int searchRadius
) {

    /** 카카오가 한 번에 돌려주는 장소 수의 상한. */
    public static final int MAX_SIZE = 15;

    /** 카카오가 받는 반경의 상한(m). 이 값을 넘겨 부르면 요청이 거절된다. */
    public static final int MAX_RADIUS_METERS = 20_000;

    public KakaoLocalProperties {
        size = clamp(size, 1, MAX_SIZE, MAX_SIZE);
        searchRadius = clamp(searchRadius, 1, MAX_RADIUS_METERS, MAX_RADIUS_METERS);
    }

    /**
     * 키가 있는지 확인한다.
     *
     * <p>키 없이 부르면 카카오가 모두 401 로 답한다. 매핑 배치는 첫 호출을 내기 전에 이걸로
     * 판단해 이유를 남기고 끝낸다. 호출을 한 번 내서 401 을 받고 멈추는 것과 달리, 무엇이
     * 빠졌는지가 로그에 그대로 남는다.
     */
    public boolean hasCredentials() {
        return restApiKey != null && !restApiKey.isBlank();
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }

        return Math.max(min, Math.min(value, max));
    }
}
