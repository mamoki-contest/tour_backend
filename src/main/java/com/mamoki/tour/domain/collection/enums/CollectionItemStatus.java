package com.mamoki.tour.domain.collection.enums;

/**
 * 저장해 둔 식별자 하나의 재조회 결과.
 *
 * <p>{@link #NOT_FOUND} 와 {@link #UNAVAILABLE} 을 반드시 구분한다. 공급자를 부르지 못해
 * 확인하지 못한 항목을 없어진 항목으로 표시하면, 사용자가 저장해 둔 장소가 사라진 것처럼
 * 보이고 지우라는 안내까지 받게 된다.
 */
public enum CollectionItemStatus {

    /** 최신 표시정보를 얻었다. */
    AVAILABLE,

    /** 공급자에 더 이상 없는 식별자다. 저장 목록에서 정리해도 된다. */
    NOT_FOUND,

    /** 공급자를 부르지 못해 이번에는 확인하지 못했다. 없어진 것이 아니므로 지우지 않는다. */
    UNAVAILABLE
}
