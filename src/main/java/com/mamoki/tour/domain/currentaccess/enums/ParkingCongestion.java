package com.mamoki.tour.domain.currentaccess.enums;

/**
 * 주차장 하나의 혼잡 상태.
 *
 * <p>잔여 비율(잔여면 ÷ 전체 주차면)로 끊는다. 방문 혼잡도 예측과 달리 여기는 등급을
 * 만들어도 된다. 예측 집중률은 공식 기준이 없어 장소 사이 비교가 불가능했지만, 잔여
 * 비율은 그 주차장 자신의 전체 주차면 대비 값이라 뜻이 분명하다.
 *
 * <p>경계는 상수로 고정한다. README 에 같은 값을 적어 둔다.
 */
public enum ParkingCongestion {

    /** 잔여 비율 30% 이상. */
    PLENTY,

    /** 잔여 비율 10% 이상 30% 미만. */
    MODERATE,

    /** 잔여면은 있으나 비율이 10% 미만. */
    CROWDED,

    /** 잔여면 0. */
    FULL;

    /** 이 비율 이상이면 여유. */
    public static final double PLENTY_RATIO = 0.30;

    /** 이 비율 이상이면 보통. 아래면 혼잡. */
    public static final double MODERATE_RATIO = 0.10;

    /**
     * @param totalLots     전체 주차면
     * @param availableLots 잔여면
     * @return 전체 주차면이 0 이하라 비율을 낼 수 없으면 null. 0 으로 나누어 만차로 만들지 않는다
     */
    public static ParkingCongestion of(int totalLots, int availableLots) {
        if (totalLots <= 0 || availableLots < 0) {
            return null;
        }

        if (availableLots == 0) {
            return FULL;
        }

        double ratio = (double) availableLots / totalLots;

        if (ratio >= PLENTY_RATIO) {
            return PLENTY;
        }

        return ratio >= MODERATE_RATIO ? MODERATE : CROWDED;
    }
}
