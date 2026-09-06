package com.mamoki.tour.global.enums;

/**
 * 외부 데이터의 신뢰 상태.
 *
 * <p>PRD 공통 원칙상 결측을 낮은 값으로 대체하지 않는다. 값이 없다는 사실 자체를
 * {@link #NO_DATA} 로 남기고, 다른 신호로 추정하지 않는다.
 */
public enum DataStatus {

    /** 유효 기간 안의 정상 데이터. */
    AVAILABLE,

    /** 갱신에 실패해 최종 정상 데이터로 응답하는 상태. 기준 시점을 함께 노출한다. */
    STALE,

    /** 공급자가 값을 제공하지 않거나 커버리지 밖. 정보 없음으로 표시한다. */
    NO_DATA
}
