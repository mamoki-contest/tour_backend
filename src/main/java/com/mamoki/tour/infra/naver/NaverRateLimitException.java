package com.mamoki.tour.infra.naver;

import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;

/**
 * 네이버 API HUB 호출 한도 초과.
 *
 * <p>이번 수집 주기에는 더 이상 진행할 수 없다. 부분 수집분으로 스냅샷을 만들지 않고
 * 직전 정상 스냅샷을 유지한다.
 */
public class NaverRateLimitException extends ExternalApiException {

    public NaverRateLimitException(String msg) {
        super(ApiProvider.NAVER_BLOG_SEARCH, msg);
    }
}
