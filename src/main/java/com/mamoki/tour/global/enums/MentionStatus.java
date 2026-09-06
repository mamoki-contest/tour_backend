package com.mamoki.tour.global.enums;

/**
 * 관광지 한 곳의 온라인 언급량 상태.
 *
 * <p>정상 0건과 값을 얻지 못한 상태를 반드시 구분한다. 둘을 섞으면 이름이 실제 표기와
 * 달라 0 이 나온 유명 관광지가 `언급 적은 순` 최상단으로 올라온다.
 */
public enum MentionStatus {

    /** 정상 수집. total 이 유효하며 정렬에 쓴다. 0 건도 정상일 수 있다. */
    COLLECTED,

    /** 이름이 모호해 값을 그 장소의 언급량으로 볼 수 없다. 정렬에서 제외한다. */
    AMBIGUOUS,

    /** 검색어를 만들 수 없어 수집 대상이 아니다. */
    UNAVAILABLE,

    /** 호출에 실패해 값을 얻지 못했다. */
    COLLECTION_FAILED
}
