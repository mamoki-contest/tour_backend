package com.mamoki.tour.global.exception;

import com.mamoki.tour.global.rsdata.RsData;

/**
 * 서비스 계층에서 의도적으로 발생시키는 예외.
 *
 * <p>외부 공급자의 결측이나 장애는 이 예외로 다루지 않는다. 그것은 오류가 아니라
 * 정상 응답의 한 상태이므로, 캐시·폴백 계층에서 흡수해 {@code 정보 없음} 상태로 변환한다.
 */
public class ServiceException extends RuntimeException {

    private final String resultCode;

    public ServiceException(String resultCode, String msg) {
        super(msg);
        this.resultCode = resultCode;
    }

    public ServiceException(String resultCode, String msg, Throwable cause) {
        super(msg, cause);
        this.resultCode = resultCode;
    }

    public String getResultCode() {
        return resultCode;
    }

    public RsData<Void> getRsData() {
        return RsData.of(resultCode, getMessage());
    }

    public int getStatusCode() {
        return getRsData().statusCode();
    }
}
