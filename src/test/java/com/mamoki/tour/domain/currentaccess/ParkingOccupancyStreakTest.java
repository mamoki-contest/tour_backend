package com.mamoki.tour.domain.currentaccess;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.currentaccess.entity.ParkingOccupancyStreak;

/**
 * 센서 고착 판정.
 *
 * <p>실측에서 31분 내내 4곳이 점유 0, 3곳이 점유==전체로 고정이었다. 경계값이 오래
 * 변하지 않으면 그 주차장의 실시간 값을 쓰지 않는다.
 */
class ParkingOccupancyStreakTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 19, 21, 0);
    private static final Duration THRESHOLD = Duration.ofMinutes(20);

    private ParkingOccupancyStreak streak() {
        return ParkingOccupancyStreak.startFor("PLOT000001");
    }

    @Test
    @DisplayName("한 번만 본 값은 아직 고착이 아니다")
    void singleObservationIsNotStuck() {
        ParkingOccupancyStreak streak = streak();
        streak.observe(0, 48, T0);

        assertThat(streak.isStuck(THRESHOLD)).isFalse();
    }

    @Test
    @DisplayName("점유 0 이 기준 시간 이상 그대로면 고착이다")
    void detectsStuckAtZero() {
        ParkingOccupancyStreak streak = streak();
        streak.observe(0, 48, T0);
        streak.observe(0, 48, T0.plusMinutes(10));
        streak.observe(0, 48, T0.plusMinutes(20));

        assertThat(streak.isStuck(THRESHOLD)).isTrue();
    }

    @Test
    @DisplayName("점유가 전체와 같은 상태로 굳어도 고착이다")
    void detectsStuckAtFull() {
        ParkingOccupancyStreak streak = streak();
        streak.observe(170, 170, T0);
        streak.observe(170, 170, T0.plusMinutes(12));
        streak.observe(170, 170, T0.plusMinutes(25));

        assertThat(streak.isStuck(THRESHOLD)).isTrue();
    }

    /**
     * 이 장부는 사용자가 상세를 열 때만 갱신된다. 조회가 드문 새벽에는 관측 두 번 사이에
     * 20분이 지날 수 있다. 그것만으로 정말 텅 빈 주차장을 정보 없음으로 내리면, 맞는
     * 정보를 지켜 주려던 사용자에게서 빼앗게 된다.
     */
    @Test
    @DisplayName("시간을 넘겼어도 관측이 두 번뿐이면 아직 고착이 아니다")
    void requiresMinimumObservations() {
        ParkingOccupancyStreak streak = streak();
        streak.observe(0, 48, T0);
        streak.observe(0, 48, T0.plusHours(3));

        assertThat(streak.getObservationCount()).isEqualTo(2);
        assertThat(streak.isStuck(THRESHOLD)).isFalse();

        // 세 번째 관측에서 비로소 판정한다.
        streak.observe(0, 48, T0.plusHours(3).plusMinutes(5));

        assertThat(streak.isStuck(THRESHOLD)).isTrue();
    }

    @Test
    @DisplayName("값이 바뀌면 관측 횟수도 1 로 되감는다")
    void resetsObservationCountWhenValueChanges() {
        ParkingOccupancyStreak streak = streak();
        streak.observe(0, 48, T0);
        streak.observe(0, 48, T0.plusMinutes(10));
        streak.observe(0, 48, T0.plusMinutes(20));

        assertThat(streak.getObservationCount()).isEqualTo(3);

        streak.observe(5, 48, T0.plusMinutes(30));

        assertThat(streak.getObservationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("기준 시간을 채우기 전에는 고착으로 보지 않는다")
    void doesNotDetectBeforeThreshold() {
        ParkingOccupancyStreak streak = streak();
        streak.observe(0, 48, T0);
        streak.observe(0, 48, T0.plusMinutes(19));

        assertThat(streak.isStuck(THRESHOLD)).isFalse();
    }

    @Test
    @DisplayName("경계값이 아니면 아무리 오래 그대로여도 고착이 아니다")
    void ignoresStableMiddleValues() {
        ParkingOccupancyStreak streak = streak();
        streak.observe(37, 91, T0);
        streak.observe(37, 91, T0.plusHours(3));

        // 한산한 주차장은 실제로 몇 시간 동안 값이 변하지 않는다.
        assertThat(streak.isStuck(THRESHOLD)).isFalse();
    }

    @Test
    @DisplayName("값이 한 번이라도 바뀌면 연속 시간을 되감는다")
    void resetsStreakWhenValueChanges() {
        ParkingOccupancyStreak streak = streak();
        streak.observe(0, 48, T0);
        streak.observe(0, 48, T0.plusMinutes(19));
        streak.observe(3, 48, T0.plusMinutes(20));
        streak.observe(0, 48, T0.plusMinutes(21));
        streak.observe(0, 48, T0.plusMinutes(30));

        // 마지막 0 은 21분부터 9분 지났을 뿐이다.
        assertThat(streak.isStuck(THRESHOLD)).isFalse();
    }

    @Test
    @DisplayName("전체 주차면이 바뀌어도 연속 시간을 되감는다")
    void resetsStreakWhenTotalChanges() {
        ParkingOccupancyStreak streak = streak();
        streak.observe(0, 48, T0);
        streak.observe(0, 50, T0.plusMinutes(25));

        assertThat(streak.isStuck(THRESHOLD)).isFalse();
    }

    @Test
    @DisplayName("같은 관측 시각을 다시 넣어도 연속 시간과 관측 횟수가 늘지 않는다")
    void ignoresRepeatedObservationTime() {
        ParkingOccupancyStreak streak = streak();
        streak.observe(0, 48, T0);
        streak.observe(0, 48, T0.plusMinutes(12));
        streak.observe(0, 48, T0.plusMinutes(25));

        assertThat(streak.isStuck(THRESHOLD)).isTrue();
        assertThat(streak.getLastObservedAt()).isEqualTo(T0.plusMinutes(25));
        assertThat(streak.getObservationCount()).isEqualTo(3);

        // 캐시가 적중해 같은 시각이 다시 들어온 경우. 마지막 관측 시각이 밀리면 안 된다.
        streak.observe(0, 48, T0.plusMinutes(25));
        streak.observe(0, 48, T0.plusMinutes(10));

        assertThat(streak.getLastObservedAt()).isEqualTo(T0.plusMinutes(25));
        assertThat(streak.getStreakSince()).isEqualTo(T0);
        assertThat(streak.getObservationCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("한 번도 관측하지 않았으면 판정하지 않는다")
    void doesNotJudgeWithoutObservation() {
        assertThat(streak().isStuck(THRESHOLD)).isFalse();
    }
}
