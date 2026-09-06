package com.mamoki.tour.global.rsdata;

/**
 * 사용 중인 resultCode 목록.
 *
 * <p>앞자리가 HTTP 상태 코드이며, 뒷자리는 같은 상태 안에서의 구분 번호다.
 * 새 코드를 만들 때 여기에 함께 추가한다.
 */
public final class ResultCodes {

    /** 조회 성공. */
    public static final String OK = "200-1";

    /** 요청 값 검증 실패. data 에 필드별 오류가 들어간다. */
    public static final String INVALID_REQUEST = "400-1";

    /** 제약 조건 위반. */
    public static final String CONSTRAINT_VIOLATION = "400-2";

    /** 필수 파라미터 누락. */
    public static final String MISSING_PARAMETER = "400-3";

    /** 파라미터 형식 불일치. */
    public static final String TYPE_MISMATCH = "400-4";

    /** 요청한 경로 없음. */
    public static final String NOT_FOUND = "404-1";

    /** 서버 내부 오류. */
    public static final String INTERNAL_ERROR = "500-1";

    private ResultCodes() {
    }
}
