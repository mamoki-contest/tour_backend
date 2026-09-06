package com.mamoki.tour.global.enums;

/**
 * 관광지에 TMAP 시·군 검색순위를 붙일 수 있는지 여부.
 *
 * <p>TMAP 공개 파일은 시·군마다 상위 장소만 수록한다. 수록되지 않은 장소를 0 이나
 * 낮은 순위로 표현하지 않고 {@link #NOT_AVAILABLE} 로 구분한다.
 *
 * <p>이 값은 보조 근거이며 목록 정렬에 쓰지 않는다. 정렬 주 지표는 온라인 언급량이다.
 */
public enum TmapRankStatus {

    /** 활성 스냅샷에 확정 매칭된 시·군 내 순위가 있다. */
    AVAILABLE,

    /** 파일에 수록되지 않았거나 매칭 신뢰도가 낮아 순위를 붙이지 못했다. */
    NOT_AVAILABLE
}
