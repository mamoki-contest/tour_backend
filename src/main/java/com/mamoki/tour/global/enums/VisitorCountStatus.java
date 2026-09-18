package com.mamoki.tour.global.enums;

/**
 * 입장객 수치의 공표 단계.
 *
 * <p>공식 파일에는 이 표시가 들어 있지 않다. 관광지식정보시스템의 공표 규칙(확정치는 이듬해
 * 4월 공표)으로 판단하며, 파일 내용으로 추정하지 않는다.
 */
public enum VisitorCountStatus {

    /** 잠정치. 이후 공표에서 값이 바뀔 수 있다. */
    PROVISIONAL,

    /** 확정치. 이듬해 4월 공표를 지난 기간이다. */
    CONFIRMED
}
