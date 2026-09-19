package com.mamoki.tour.domain.placemapping.enums;

/**
 * 매핑 한 행의 상태.
 *
 * <p>{@link #CONFIRMED} 만 조회 응답에 값을 만든다. 나머지 둘은 무엇을 시도했고 왜 확정하지
 * 못했는지를 남기는 이력이며, 다음 실행과 운영자 판단의 단서가 된다.
 *
 * <p>{@link #LOW_CONFIDENCE} 를 값으로 쓰지 않는 것이 이 표의 핵심이다. 저신뢰 매칭으로
 * 숫자를 만들면 틀린 장소에 입장객 수나 검색순위가 붙는데, 에러가 나지 않아 아무도 모른다.
 */
public enum PlaceMappingStatus {

    /** 한 곳으로 좁혀졌다. 조회 응답에 값을 만든다. */
    CONFIRMED,

    /** 후보는 찾았으나 한 곳으로 좁히지 못했다. 값을 만들지 않는다. */
    LOW_CONFIDENCE,

    /** 이을 후보를 하나도 찾지 못했다. 값을 만들지 않는다. */
    UNMATCHED
}
