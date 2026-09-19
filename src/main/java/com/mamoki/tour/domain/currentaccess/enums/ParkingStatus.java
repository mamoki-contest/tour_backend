package com.mamoki.tour.domain.currentaccess.enums;

/**
 * 관광지 주변 주차 여건의 상태.
 *
 * <p>네 상태를 반드시 구분한다. {@code NONE} 과 {@code NO_DATA} 를 같은 문구로 표시하면
 * "물어봤는데 없더라" 와 "물어보지도 못했다" 가 섞인다. {@code STATIC_ONLY} 를
 * {@code AVAILABLE} 로 올리면 반기마다 갱신되는 정적 정보가 현재 상태로 위장된다.
 */
public enum ParkingStatus {

    /** 반경 안에 실시간 잔여면을 아는 주차장이 있다. */
    AVAILABLE,

    /**
     * 반경 안에 주차장은 있으나 지금 자리가 있는지는 모른다.
     *
     * <p>표준데이터에만 있는 주차장이거나, 실시간 공급자의 주차장인데 값을 받지 못했거나
     * 센서가 고착된 경우다. 이름·총 주차면·거리까지만 말할 수 있다.
     */
    STATIC_ONLY,

    /** 두 공급자를 모두 확인했고 반경 안에 주차장이 없다. */
    NONE,

    /** 좌표가 없거나 공급자를 확인하지 못해 있다 없다를 말할 수 없다. */
    NO_DATA
}
