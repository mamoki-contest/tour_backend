package com.mamoki.tour.global.enums;

/**
 * 관광지별 관심도 산정 여부.
 *
 * <p>미산정을 낮은 관심도로 취급하지 않는다. 덜 알려진 순 정렬에서도
 * {@link #NOT_CALCULATED} 장소를 상단으로 올리지 않는다.
 */
public enum InterestStatus {

    /** 활성 스냅샷에 확정 매칭된 관심도 값이 있다. */
    CALCULATED,

    /** 스냅샷에 없거나 매칭 신뢰도가 낮아 값을 확정하지 못했다. */
    NOT_CALCULATED
}
