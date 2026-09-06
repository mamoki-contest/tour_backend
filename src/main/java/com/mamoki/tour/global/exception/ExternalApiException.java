package com.mamoki.tour.global.exception;

import com.mamoki.tour.global.enums.ApiProvider;

/**
 * 외부 공급자 호출 실패.
 *
 * <p>이 예외는 컨트롤러까지 전파되면 안 된다. 캐시·폴백 계층에서 흡수해 최종 정상 데이터로
 * 응답하거나, 그것도 없으면 {@code DataStatus.NO_DATA} 로 변환한다. 공급자 장애는 오류가
 * 아니라 정상 응답의 한 상태라는 PRD 공통 원칙을 지키기 위한 구분이다.
 */
public class ExternalApiException extends RuntimeException {

    private final ApiProvider provider;

    public ExternalApiException(ApiProvider provider, String msg) {
        super(msg);
        this.provider = provider;
    }

    public ExternalApiException(ApiProvider provider, String msg, Throwable cause) {
        super(msg, cause);
        this.provider = provider;
    }

    public ApiProvider getProvider() {
        return provider;
    }
}
