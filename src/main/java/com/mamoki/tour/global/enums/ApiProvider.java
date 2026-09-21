package com.mamoki.tour.global.enums;

/**
 * 외부 데이터 공급자.
 *
 * <p>PRD 가 확정한 공급자만 둔다. 카카오맵 JavaScript SDK 는 프론트 전용이라 포함하지 않는다.
 * {@link #KAKAO_LOCAL} 은 지도 표시가 아니라 공급자 간 장소 매핑(#55)에만 쓰는 서버 전용
 * REST API 라 별개다.
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

    /**
     * 한국교통안전공단 주차정보.
     *
     * <p>심의승인 대기 중이라 아직 부르지 않는다. 연계 지자체 목록이 비공개라 승인되어도
     * 강원 데이터가 나온다는 보장이 없어, 실시간 주차는 {@link #GN_ITS_PARKING} 으로 갔다.
     */
    TS_PARKING,

    /** 강릉시 교통정보 조회서비스. 강원에서 유일하게 실시간 주차를 여는 지자체 API 다. */
    GN_ITS_PARKING,

    /** 네이버 블로그 검색. 온라인 언급량 정렬의 주 지표. */
    NAVER_BLOG_SEARCH,

    /**
     * 네이버 이미지 검색. 공급자 사진이 없는 관광지의 대표 사진 보강(#99)에만 쓴다.
     *
     * <p>블로그 검색과 같은 허브·같은 키를 쓰지만 별개로 둔다. 허브가 Application 마다
     * API 를 따로 켜므로 하나가 막혀도 다른 하나는 돌아간다 — 로그에서 어느 쪽이 막혔는지
     * 가릴 수 있어야 한다.
     */
    NAVER_IMAGE_SEARCH,

    /** 카카오 로컬 키워드 검색. 원천 이름을 좌표로 옮겨 카탈로그에 잇는 데만 쓴다. */
    KAKAO_LOCAL
}
