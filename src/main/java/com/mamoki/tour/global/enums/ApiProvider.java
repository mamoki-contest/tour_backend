package com.mamoki.tour.global.enums;

/**
 * 외부 데이터 공급자.
 *
 * <p>PRD 가 확정한 공급자만 둔다. 카카오맵은 프론트 전용이라 포함하지 않는다.
 */
public enum ApiProvider {

    /** 한국관광공사 국문 관광정보 서비스. 관광지 기본정보·검색. */
    KOR_SERVICE2,

    /** 관광지별 향후 30일 방문 혼잡도 예측. */
    TATS_CNCTR_RATE,

    /** 연관 관광지·음식점·숙박 후보. */
    TAR_RLTE_TAR,

    /** 시·군 내부 중심관광지 순위. */
    LOCGO_HUB_TAR,

    /** 한국관광공사 빅데이터 지역별 방문자수. */
    REGION_VISITOR,

    /** 티맵 관광지 관심지점 집계. */
    TMAP_INTEREST,

    /** 국가교통정보센터 교통소통정보. */
    ITS_TRAFFIC,

    /** 한국교통안전공단 주차정보. */
    TS_PARKING,

    /** 네이버 블로그 검색. 온라인 언급량 정렬의 주 지표. */
    NAVER_BLOG_SEARCH
}
