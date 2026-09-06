package com.mamoki.tour.infra.naver;

import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;

/**
 * 네이버 API HUB 인증 실패.
 *
 * <p>키가 잘못되었거나 권한이 없는 상태라 남은 호출을 계속해도 모두 실패한다.
 * 배치는 이 예외를 만나면 즉시 중단하고 직전 정상 스냅샷을 유지해야 한다.
 */
public class NaverAuthenticationException extends ExternalApiException {

    public NaverAuthenticationException(String msg) {
        super(ApiProvider.NAVER_BLOG_SEARCH, msg);
    }
}
