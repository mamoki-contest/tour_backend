package com.mamoki.tour.infra.kakao;

import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;

/**
 * 카카오 로컬 API 호출 한도 초과.
 *
 * <p>로컬 검색은 하루 100,000회다. 한도를 넘으면 오늘은 더 진행할 수 없으므로 배치가 그
 * 자리에서 멈춘다. 이미 판정한 매핑은 그대로 남고, 남은 이름은 다음 실행이 이어서 본다.
 */
public class KakaoRateLimitException extends ExternalApiException {

    public KakaoRateLimitException(String msg) {
        super(ApiProvider.KAKAO_LOCAL, msg);
    }
}
