package com.mamoki.tour.domain.currentaccess.entity;

import java.time.Duration;
import java.time.LocalDateTime;

import com.mamoki.tour.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주차장 하나의 점유 값이 얼마나 오래 그대로인지.
 *
 * <p><b>센서 고착을 걸러내기 위한 장부다.</b> 실측에서 31분 관측 내내 4곳이 점유 0, 3곳이
 * 점유==전체로 고정이었다. 둘 다 경계값이라 "정말 텅 빈 주차장" 과 "센서가 0 에 붙은
 * 주차장" 이 응답에서 구분되지 않는다. 0 을 그대로 내보내면 만차인 곳으로 사람을 보내게
 * 된다.
 *
 * <p>그래서 경계값(0 또는 전체)이 정해진 시간 이상 한 번도 변하지 않으면 그 주차장의
 * 실시간 값을 쓰지 않는다. 값을 고치지 않고 <b>쓰지 않을 뿐</b>이다. 경계값이 아닌 값은
 * 아무리 오래 그대로여도 고착으로 보지 않는다. 한산한 주차장은 실제로 변하지 않는다.
 *
 * <p>관측 시각은 캐시가 공급자를 실제로 부른 시각이다. 같은 시각을 다시 넣으면 아무 일도
 * 일어나지 않아, 캐시 적중이 반복돼도 연속 시간이 부풀지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "parking_occupancy_streak",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_parking_occupancy_streak_prk_id", columnNames = "prk_id")
)
public class ParkingOccupancyStreak extends BaseEntity {

    /** 강릉시 교통정보 조회서비스의 주차장 식별자. */
    @Column(name = "prk_id", nullable = false, length = 40)
    private String prkId;

    /** 마지막으로 본 점유 대수. 아직 한 번도 못 봤으면 null. */
    @Column(name = "last_occupied_lots")
    private Integer lastOccupiedLots;

    @Column(name = "last_total_lots")
    private Integer lastTotalLots;

    /** 지금 값이 처음 나타난 시각. 값이 바뀔 때마다 여기로 되감는다. */
    @Column(name = "streak_since")
    private LocalDateTime streakSince;

    /** 마지막 관측 시각. 같은 시각을 다시 받으면 같은 관측으로 본다. */
    @Column(name = "last_observed_at")
    private LocalDateTime lastObservedAt;

    @Builder
    private ParkingOccupancyStreak(String prkId, Integer lastOccupiedLots, Integer lastTotalLots,
                                   LocalDateTime streakSince, LocalDateTime lastObservedAt) {
        this.prkId = prkId;
        this.lastOccupiedLots = lastOccupiedLots;
        this.lastTotalLots = lastTotalLots;
        this.streakSince = streakSince;
        this.lastObservedAt = lastObservedAt;
    }

    public static ParkingOccupancyStreak startFor(String prkId) {
        return ParkingOccupancyStreak.builder().prkId(prkId).build();
    }

    /**
     * 한 번의 관측을 반영한다.
     *
     * <p>이전과 같은 시각이거나 더 과거면 아무것도 하지 않는다. 캐시가 적중해 같은 본문을
     * 다시 돌려준 경우인데, 그것을 새 관측으로 세면 실제로는 한 번만 본 값이 오래 유지된
     * 것처럼 보인다.
     */
    public void observe(int occupiedLots, int totalLots, LocalDateTime observedAt) {
        if (observedAt == null) {
            return;
        }

        if (lastObservedAt != null && !observedAt.isAfter(lastObservedAt)) {
            return;
        }

        boolean changed = lastOccupiedLots == null
                || lastOccupiedLots != occupiedLots
                || lastTotalLots == null
                || lastTotalLots != totalLots;

        if (changed) {
            this.streakSince = observedAt;
        }

        this.lastOccupiedLots = occupiedLots;
        this.lastTotalLots = totalLots;
        this.lastObservedAt = observedAt;
    }

    /**
     * 고착 판정.
     *
     * @param threshold 경계값이 이 시간 이상 변하지 않으면 고착으로 본다
     * @return 이 주차장의 실시간 값을 쓰면 안 되는지
     */
    public boolean isStuck(Duration threshold) {
        if (lastOccupiedLots == null || streakSince == null || lastObservedAt == null) {
            return false;
        }

        if (!atBoundary()) {
            return false;
        }

        return !Duration.between(streakSince, lastObservedAt).minus(threshold).isNegative();
    }

    /**
     * 경계값인지. 점유 0(텅 빔) 또는 점유==전체(만차).
     *
     * <p>고착된 센서가 멈추는 자리가 이 둘이다. 가운데 값은 센서가 실제로 세고 있다는 뜻이라
     * 오래 그대로여도 의심하지 않는다.
     */
    private boolean atBoundary() {
        return lastOccupiedLots == 0
                || (lastTotalLots != null && lastOccupiedLots.equals(lastTotalLots));
    }
}
