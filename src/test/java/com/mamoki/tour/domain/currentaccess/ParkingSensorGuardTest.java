package com.mamoki.tour.domain.currentaccess;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mamoki.tour.domain.currentaccess.repository.ParkingOccupancyStreakRepository;
import com.mamoki.tour.domain.currentaccess.service.ParkingSensorGuard;
import com.mamoki.tour.infra.gnits.dto.RealtimeParkingLot;

/**
 * 센서 고착 장부가 요청 사이에 남는지 확인한다.
 *
 * <p>고착 판정은 여러 번의 관측을 견주는 일이라 한 요청 안에서는 결론이 나지 않는다.
 * 장부가 저장되지 않으면 판정이 영원히 "아직 모름" 에 머문다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ParkingSensorGuardTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 19, 21, 0);

    @Autowired
    private ParkingSensorGuard sensorGuard;

    @Autowired
    private ParkingOccupancyStreakRepository streakRepository;

    @BeforeEach
    void reset() {
        streakRepository.deleteAllInBatch();
    }

    private static RealtimeParkingLot lot(String prkId, int total, int occupied) {
        return new RealtimeParkingLot(prkId, prkId, null,
                new BigDecimal("37.75"), new BigDecimal("128.89"), "공영",
                total, occupied, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("첫 관측만으로는 고착으로 보지 않는다")
    void doesNotJudgeOnFirstObservation() {
        Set<String> stuck = sensorGuard.stuckLotIds(List.of(lot("PLOT000001", 48, 0)), T0);

        assertThat(stuck).isEmpty();
        assertThat(streakRepository.findByPrkIdIn(List.of("PLOT000001"))).hasSize(1);
    }

    @Test
    @DisplayName("경계값이 기준 시간 이상 그대로면 다음 요청에서 고착으로 걸린다")
    void detectsStuckAcrossRequests() {
        sensorGuard.stuckLotIds(List.of(lot("PLOT000001", 48, 0)), T0);

        Set<String> stuck = sensorGuard.stuckLotIds(List.of(lot("PLOT000001", 48, 0)),
                T0.plus(ParkingSensorGuard.STUCK_THRESHOLD));

        assertThat(stuck).containsExactly("PLOT000001");
    }

    @Test
    @DisplayName("값이 변한 주차장은 고착이 아니다")
    void clearsStuckWhenValueChanges() {
        sensorGuard.stuckLotIds(List.of(lot("PLOT000011", 410, 98)), T0);
        sensorGuard.stuckLotIds(List.of(lot("PLOT000011", 410, 0)), T0.plusMinutes(10));

        Set<String> stuck = sensorGuard.stuckLotIds(List.of(lot("PLOT000011", 410, 102)),
                T0.plusMinutes(40));

        assertThat(stuck).isEmpty();
    }

    @Test
    @DisplayName("캐시가 적중해 같은 관측 시각이 반복돼도 연속 시간이 부풀지 않는다")
    void ignoresRepeatedObservations() {
        sensorGuard.stuckLotIds(List.of(lot("PLOT000006", 170, 170)), T0);

        for (int i = 0; i < 5; i++) {
            assertThat(sensorGuard.stuckLotIds(List.of(lot("PLOT000006", 170, 170)), T0)).isEmpty();
        }
    }

    @Test
    @DisplayName("고착된 주차장만 가려낸다")
    void picksOnlyStuckLots() {
        List<RealtimeParkingLot> lots = List.of(
                lot("PLOT000001", 48, 0),
                lot("PLOT000006", 170, 170),
                lot("PLOT000002", 91, 37));

        sensorGuard.stuckLotIds(lots, T0);
        Set<String> stuck = sensorGuard.stuckLotIds(lots, T0.plusMinutes(30));

        assertThat(stuck).containsExactlyInAnyOrder("PLOT000001", "PLOT000006");
    }

    @Test
    @DisplayName("실시간 값이 없는 주차장은 장부에 담지 않는다")
    void skipsLotsWithoutRealtime() {
        RealtimeParkingLot unknown = new RealtimeParkingLot("PLOT000009", "아르떼뮤지엄", null,
                new BigDecimal("37.79"), new BigDecimal("128.90"), "공영",
                null, null, null, null, null, null, null, null);

        assertThat(sensorGuard.stuckLotIds(List.of(unknown), T0)).isEmpty();
        assertThat(streakRepository.count()).isZero();
    }

    @Test
    @DisplayName("관측 시각을 모르면 장부를 건드리지 않는다")
    void skipsWithoutObservationTime() {
        assertThat(sensorGuard.stuckLotIds(List.of(lot("PLOT000001", 48, 0)), null)).isEmpty();
        assertThat(streakRepository.count()).isZero();
    }
}
