package com.mamoki.tour.infra.kakao;

import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;

/**
 * 카카오 로컬 API 인증 실패.
 *
 * <p>키가 잘못되었거나 권한이 없는 상태라 남은 호출을 계속해도 모두 실패한다. 매핑 배치는
 * 이 예외를 만나면 그 자리에서 멈춘다. 계속 부르면 한도만 깎고 얻는 것이 없다.
 */
public class KakaoAuthenticationException extends ExternalApiException {

    public KakaoAuthenticationException(String msg) {
        super(ApiProvider.KAKAO_LOCAL, msg);
    }
}
