package com.mamoki.tour.domain.parking.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.parking.entity.ParkingLotSnapshot;
import com.mamoki.tour.domain.parking.repository.ParkingLotRepository;
import com.mamoki.tour.domain.parking.repository.ParkingLotSnapshotRepository;
import com.mamoki.tour.global.enums.SnapshotStatus;

/**
 * 주차장 표준데이터 스냅샷의 활성화와 이력 관리.
 *
 * <p>교체는 한 트랜잭션 안에서 일어난다. 새 스냅샷을 활성화하는 순간에만 직전 스냅샷이
 * 물러나므로, 조회 쪽에서 활성 스냅샷이 잠깐 사라지는 구간이 없다.
 *
 * <p>적재에 실패하면 새 스냅샷만 FAILED 로 남고 직전 정상 스냅샷이 활성 상태를 지킨다.
 */
@Service
public class ParkingLotSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(ParkingLotSnapshotService.class);

    private final ParkingLotSnapshotRepository snapshotRepository;
    private final ParkingLotRepository lotRepository;

    public ParkingLotSnapshotService(ParkingLotSnapshotRepository snapshotRepository,
                                     ParkingLotRepository lotRepository) {
        this.snapshotRepository = snapshotRepository;
        this.lotRepository = lotRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ParkingLotSnapshot> findActive() {
        return snapshotRepository.findByStatus(SnapshotStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<ParkingLotSnapshot> findImportHistory() {
        return snapshotRepository.findAllByOrderByImportedAtDesc();
    }

    /**
     * @throws IllegalStateException 적재된 행이 하나도 없는 경우.
     *                               빈 스냅샷으로 교체하면 강원 주차장이 통째로 사라진다.
     */
    @Transactional
    public ParkingLotSnapshot activate(ParkingLotSnapshot snapshot) {
        long rowCount = lotRepository.countBySnapshot(snapshot);

        if (rowCount == 0) {
            throw new IllegalStateException(
                    "적재된 행이 없어 활성화할 수 없습니다. version=" + snapshot.getVersion());
        }

        snapshotRepository.findByStatus(SnapshotStatus.ACTIVE)
                .filter(active -> !active.getId().equals(snapshot.getId()))
                .ifPresent(ParkingLotSnapshot::supersede);

        snapshot.activate((int) rowCount);

        log.info("주차장 표준데이터 스냅샷 활성화: version={}, 기준일={}, 행={}",
                snapshot.getVersion(), snapshot.getDataBaseDate(), rowCount);

        return snapshot;
    }
}
