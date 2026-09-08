package com.mamoki.tour.domain.relatedplace.enums;

/**
 * 연관 장소의 유형.
 *
 * <p>대체지 후보가 될 수 있는 것은 {@link #ATTRACTION} 뿐이다. 음식점·숙박시설은 원래 장소를
 * 대신하는 곳이 아니라 같은 여행에서 함께 가는 곳이므로 별도 묶음으로 나눈다.
 *
 * <p>{@link #OTHER} 는 공급자가 우리가 모르는 대분류를 내려줬을 때다. 관광지로 넘겨짚어
 * 대체지 후보에 넣지 않는다. 잘못 넘겨짚으면 원래 장소를 대신할 수 없는 곳을 대체지라고
 * 말하게 된다.
 */
public enum RelatedPlaceKind {

    /** 관광지. 대체지 후보 자격을 따질 수 있는 유일한 유형이다. */
    ATTRACTION,

    /** 음식점. 함께 가기 좋은 곳. */
    RESTAURANT,

    /** 숙박시설. 함께 가기 좋은 곳. */
    LODGING,

    /** 공급자가 내려준 대분류를 우리가 아직 모른다. 어느 묶음으로도 넘겨짚지 않는다. */
    OTHER
}
