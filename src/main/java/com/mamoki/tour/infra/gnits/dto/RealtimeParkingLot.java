package com.mamoki.tour.infra.gnits.dto;

import java.math.BigDecimal;

/**
 * 기본정보와 실시간 현황을 {@code prkId} 로 이어 붙인 주차장 한 곳.
 *
 * <p>두 오퍼레이션의 {@code prkId}·{@code prkName} 집합이 실측상 완전히 일치하지만, 한쪽에만
 * 있는 경우를 가정하고 잇는다. 실시간 값이 없으면 {@code totalLots}·{@code occupiedLots} 가
 * null 이고, 그 주차장은 "있다는 사실" 까지만 말한다.
 *
 * @param occupiedLots 점유 대수. 공급자 필드명이 {@code availLots} 라 잔여면으로 오해하기
 *                     쉬운데 실제로는 점유다. 잔여면은 {@link #availableLots()} 로 계산한다
 */
public record RealtimeParkingLot(
        String prkId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String parkingType,
        Integer totalLots,
        Integer occupiedLots,
        String weekOpenTime,
        String weekEndTime,
        String satOpenTime,
        String satEndTime,
        String holiOpenTime,
        String holiEndTime
) {

    /**
     * 잔여면 = 전체 주차면 − 점유 대수.
     *
     * <p>점유가 전체보다 크면 둘 중 하나가 틀린 것이라 값을 만들지 않는다. 음수 잔여면을
     * 0 으로 눌러 담으면 깨진 값이 만차로 보인다.
     *
     * @return 계산할 수 없으면 null
     */
    public Integer availableLots() {
        if (totalLots == null || occupiedLots == null) {
            return null;
        }

        if (occupiedLots < 0 || totalLots < 0 || occupiedLots > totalLots) {
            return null;
        }

        return totalLots - occupiedLots;
    }

    /** 지금 자리가 몇 개인지 말할 수 있는 상태인지. */
    public boolean hasRealtime() {
        return availableLots() != null;
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}
