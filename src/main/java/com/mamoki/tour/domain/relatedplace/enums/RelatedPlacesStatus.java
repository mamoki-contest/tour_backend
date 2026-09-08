package com.mamoki.tour.domain.relatedplace.enums;

/**
 * 연관 장소 묶음이 비어 있을 때 그 이유를 구분하는 상태.
 *
 * <p>빈 목록 하나로는 "공급자 데이터를 못 받았다" 와 "연관 장소는 있는데 자격을 충족한 곳이
 * 없다" 를 구분할 수 없다. 프론트가 둘을 같은 문구로 표시하면 없는 사실을 만들어내는 것이라
 * 상태를 따로 내려준다.
 *
 * <p>{@code DataStatus} 와는 다르다. 그쪽은 공급자 데이터의 신선도이고, 이쪽은 그 데이터로
 * 이 묶음을 채울 수 있었는지다.
 */
public enum RelatedPlacesStatus {

    /** 채울 항목이 있다. */
    AVAILABLE,

    /** 공급자 응답을 얻지 못했거나 이 관광지가 공급자의 연관 목록에 없다. */
    NO_RELATED_DATA,

    /** 연관 장소는 받았지만 이 묶음의 자격을 충족한 곳이 없다. */
    NONE_QUALIFIED
}
