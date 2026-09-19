package com.mamoki.tour.domain.currentaccess;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.currentaccess.enums.ParkingCongestion;

/** 잔여 비율로 끊는 주차 혼잡 등급. 경계는 상수이며 README 에 같은 값이 적혀 있다. */
class ParkingCongestionTest {

    @Test
    @DisplayName("잔여면이 없으면 만차다")
    void reportsFullWithoutAvailableLots() {
        assertThat(ParkingCongestion.of(170, 0)).isEqualTo(ParkingCongestion.FULL);
    }

    @Test
    @DisplayName("잔여 비율 30% 이상이면 여유다")
    void reportsPlentyAboveThirtyPercent() {
        assertThat(ParkingCongestion.of(100, 30)).isEqualTo(ParkingCongestion.PLENTY);
        assertThat(ParkingCongestion.of(410, 312)).isEqualTo(ParkingCongestion.PLENTY);
    }

    @Test
    @DisplayName("잔여 비율 10% 이상 30% 미만이면 보통이다")
    void reportsModerateBetweenTenAndThirty() {
        assertThat(ParkingCongestion.of(100, 29)).isEqualTo(ParkingCongestion.MODERATE);
        assertThat(ParkingCongestion.of(100, 10)).isEqualTo(ParkingCongestion.MODERATE);
    }

    @Test
    @DisplayName("잔여면은 있으나 비율이 10% 미만이면 혼잡이다")
    void reportsCrowdedBelowTenPercent() {
        assertThat(ParkingCongestion.of(100, 9)).isEqualTo(ParkingCongestion.CROWDED);
        assertThat(ParkingCongestion.of(410, 1)).isEqualTo(ParkingCongestion.CROWDED);
    }

    @Test
    @DisplayName("전체 주차면이 0 이면 판정하지 않는다. 0 으로 나눠 만차로 만들지 않는다")
    void refusesToJudgeWithoutTotalLots() {
        assertThat(ParkingCongestion.of(0, 0)).isNull();
        assertThat(ParkingCongestion.of(-1, 0)).isNull();
        assertThat(ParkingCongestion.of(10, -1)).isNull();
    }
}
