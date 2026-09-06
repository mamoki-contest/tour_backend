package com.mamoki.tour.domain.attraction.dto;

/**
 * 관광지 목록 정렬 기준.
 *
 * <p>온라인 언급량은 블로그 검색 결과 수다. 실제 인기·방문객 수가 아니므로 사용자 문구도
 * `인기`가 아니라 `온라인 언급`으로 맞춘다.
 */
public enum AttractionSort {

    /** 온라인 언급 많은 순. */
    ONLINE_MENTION_DESC,

    /** 온라인 언급 적은 순. 미산정 장소를 상단에 올리지 않는다. */
    ONLINE_MENTION_ASC;

    public boolean isAscending() {
        return this == ONLINE_MENTION_ASC;
    }
}
