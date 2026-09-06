package com.mamoki.tour.global.enums;

/**
 * 순위 행과 표준 관광지 카탈로그의 매칭 결과.
 *
 * <p>PRD 상 저신뢰·미매칭 결과를 확정 값으로 쓰지 않는다.
 * {@link #MATCHED} 인 행만 정렬에 사용한다.
 */
public enum CatalogMatchStatus {

    /** 표준 관광지와 확정 매칭됨. 정렬에 사용한다. */
    MATCHED,

    /** 부분 일치 등으로 신뢰도가 낮음. 정렬에 사용하지 않는다. */
    LOW_CONFIDENCE,

    /** 대응하는 표준 관광지를 찾지 못함. */
    UNMATCHED
}
