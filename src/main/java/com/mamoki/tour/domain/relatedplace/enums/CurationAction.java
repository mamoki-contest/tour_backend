package com.mamoki.tour.domain.relatedplace.enums;

/**
 * 대체지 큐레이션이 할 수 있는 일.
 *
 * <p>둘 다 <b>추천 자격을 충족한 후보</b>에만 작용한다. 자격을 만들어 주는 동작은 없다.
 * 큐레이션은 테마 적합성을 보정하는 운영 판단이지, 결측 예측을 대신하는 수단이 아니다.
 */
public enum CurationAction {

    /** 자격은 충족했지만 이 기준 관광지의 대체지로는 부적절한 곳을 뺀다. */
    EXCLUDE,

    /** 자격을 충족한 후보 중 대표로 먼저 보일 곳을 맨 앞에 둔다. */
    REPRESENTATIVE
}
