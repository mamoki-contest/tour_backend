package com.mamoki.tour.infra.naver;

import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;

/**
 * 네이버 API HUB 인증 실패.
 *
 * <p>키가 잘못되었거나 권한이 없는 상태라 남은 호출을 계속해도 모두 실패한다.
 * 배치는 이 예외를 만나면 즉시 중단하고 직전 정상 스냅샷을 유지해야 한다.
 *
 * <p>허브에서는 <b>키가 맞아도 그 Application 에서 해당 API 가 켜져 있지 않으면</b> 같은
 * 401 이 온다. 배치에게는 같은 지시(즉시 중단)라 예외를 나누지 않고, 무엇을 확인해야
 * 하는지는 메시지가 말한다.
 *
 * <p>같은 허브에 검색 종류가 여럿이라 어느 API 였는지를 함께 들고 간다. 공급자가 하나로
 * 고정되어 있으면 로그만 보고는 블로그 검색이 막힌 것인지 이미지 검색이 막힌 것인지
 * 가릴 수 없다.
 */
public class NaverAuthenticationException extends ExternalApiException {

    public NaverAuthenticationException(String msg) {
        this(ApiProvider.NAVER_BLOG_SEARCH, msg);
    }

    public NaverAuthenticationException(ApiProvider provider, String msg) {
        super(provider, msg);
    }
}
