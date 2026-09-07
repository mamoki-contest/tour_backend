package com.mamoki.tour.domain.visittiming.enums;

/**
 * 날짜 탐색 모드.
 *
 * <p>두 모드는 묻는 것이 다르다. 고정은 "이 날 이 장소가 어떤가", 유연은
 * "이 장소는 언제가 한산한가" 다. 그래서 선택일은 고정 모드에서만 받는다.
 */
public enum DateMode {

    /** 날짜를 확정하고 그 날의 장소 내부 상대 수준을 묻는다. 선택일이 필요하다. */
    FIXED,

    /** 날짜를 열어 두고 장소별 한산 예상일을 묻는다. 선택일을 받지 않는다. */
    FLEXIBLE
}
