package com.mamoki.tour.domain.currentaccess.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.currentaccess.entity.ParkingOccupancyStreak;
import com.mamoki.tour.domain.currentaccess.repository.ParkingOccupancyStreakRepository;
import com.mamoki.tour.infra.gnits.dto.RealtimeParkingLot;

/**
 * 센서가 고착된 주차장을 가려낸다.
 *
 * <p>실측에서 31분 관측 내내 4곳이 점유 0, 3곳이 점유==전체로 고정이었고 강릉역도 약 1분간
 * 98 → 0 → 98 로 튀었다. 경계값(0 또는 전체)은 실제 상태일 수도 센서 고장일 수도 있는데,
 * 그대로 내보내면 텅 빈 주차장과 죽은 센서를 사용자가 구분할 방법이 없다.
 *
 * <p>그래서 경계값이 {@link #STUCK_THRESHOLD} 이상 한 번도 변하지 않으면 그 주차장만
 * 실시간에서 빼고 {@code STATIC_ONLY} 로 내린다. 값을 고치지 않고 쓰지 않을 뿐이다.
 *
 * <p>판정 기준은 캐시 갱신 시각이다. 캐시가 적중한 요청은 새 관측이 아니므로 연속 시간을
 * 늘리지 않는다.
 */
@Service
public class ParkingSensorGuard {

    /**
     * 경계값이 이 시간 이상 그대로면 고착으로 본다.
     *
     * <p>실시간 캐시가 5분이라 네 번 연속 같은 경계값을 본 셈이다. 짧게 잡으면 실제로 텅 빈
     * 주차장이 자꾸 정보 없음이 되고, 길게 잡으면 죽은 센서를 그만큼 오래 내보내게 된다.
     * 실측 관측 창이 31분이었고 그 안에서 고착된 곳들은 한 번도 변하지 않았다.
     */
    public static final Duration STUCK_THRESHOLD = Duration.ofMinutes(20);

    private static final Logger log = LoggerFactory.getLogger(ParkingSensorGuard.class);

    private final ParkingOccupancyStreakRepository streakRepository;

    public ParkingSensorGuard(ParkingOccupancyStreakRepository streakRepository) {
        this.streakRepository = streakRepository;
    }

    /**
     * 관측을 기록하고 고착된 주차장 식별자를 돌려준다.
     *
     * @param observedAt 캐시가 이 본문을 수집한 시각. 캐시 적중이면 이전과 같은 값이 온다
     * @return 실시간 값을 쓰면 안 되는 주차장의 {@code prkId}
     */
    @Transactional
    public Set<String> stuckLotIds(List<RealtimeParkingLot> lots, LocalDateTime observedAt) {
        List<RealtimeParkingLot> measured = lots.stream()
                .filter(RealtimeParkingLot::hasRealtime)
                .toList();

        if (measured.isEmpty() || observedAt == null) {
            return Set.of();
        }

        Map<String, ParkingOccupancyStreak> streaks = loadStreaks(measured);
        Set<String> stuck = new HashSet<>();

        for (RealtimeParkingLot lot : measured) {
            ParkingOccupancyStreak streak = streaks.computeIfAbsent(lot.prkId(),
                    id -> streakRepository.save(ParkingOccupancyStreak.startFor(id)));

            streak.observe(lot.occupiedLots(), lot.totalLots(), observedAt);

            if (streak.isStuck(STUCK_THRESHOLD)) {
                stuck.add(lot.prkId());
            }
        }

        if (!stuck.isEmpty()) {
            log.info("경계값이 {}분 이상 변하지 않아 실시간에서 제외합니다: {}",
                    STUCK_THRESHOLD.toMinutes(), stuck);
        }

        return stuck;
    }

    private Map<String, ParkingOccupancyStreak> loadStreaks(List<RealtimeParkingLot> lots) {
        Map<String, ParkingOccupancyStreak> byId = new HashMap<>();

        List<String> ids = lots.stream().map(RealtimeParkingLot::prkId).toList();

        for (ParkingOccupancyStreak streak : streakRepository.findByPrkIdIn(ids)) {
            byId.put(streak.getPrkId(), streak);
        }

        return byId;
    }
}
