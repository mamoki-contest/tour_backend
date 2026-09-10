package com.mamoki.tour.domain.region.enums;

/**
 * 같은 기준 기간 안에서 시·군끼리 비교한 상대 구간.
 *
 * <p>절대 방문자 수의 등급이 아니다. 조회한 시·군 집합이 달라지면 같은 시·군의 구간도
 * 달라질 수 있다. 지도에서 색을 나누기 위한 값이며, 다른 기간의 구간과 비교하지 않는다.
 *
 * <p>값이 없는 시·군은 여기에 담지 않는다. 결측을 {@code VERY_LOW} 로 대신하면
 * 수집하지 못한 지역이 방문객이 적은 지역으로 읽힌다.
 */
public enum RegionVisitLevel {

    VERY_HIGH,
    HIGH,
    MEDIUM,
    LOW,
    VERY_LOW
}
