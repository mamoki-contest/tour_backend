package com.mamoki.tour.domain.visitorstats.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.visitorstats.entity.VisitorStatsSnapshot;
import com.mamoki.tour.domain.visitorstats.repository.VisitorStatsEntryRepository;
import com.mamoki.tour.domain.visitorstats.repository.VisitorStatsSnapshotRepository;
import com.mamoki.tour.global.enums.SnapshotStatus;

/**
 * 입장객통계 스냅샷의 활성화와 이력 관리.
 *
 * <p>교체는 한 트랜잭션 안에서 일어난다. 새 스냅샷을 활성화하는 순간에만 직전 스냅샷이
 * 물러나므로, 조회 쪽에서 활성 스냅샷이 잠깐 사라지는 구간이 없다.
 *
 * <p>적재에 실패하면 새 스냅샷만 FAILED 로 남고 직전 정상 스냅샷이 활성 상태를 지킨다.
 */
@Service
public class VisitorStatsSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(VisitorStatsSnapshotService.class);

    private final VisitorStatsSnapshotRepository snapshotRepository;
    private final VisitorStatsEntryRepository entryRepository;

    public VisitorStatsSnapshotService(VisitorStatsSnapshotRepository snapshotRepository,
                                       VisitorStatsEntryRepository entryRepository) {
        this.snapshotRepository = snapshotRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional(readOnly = true)
    public Optional<VisitorStatsSnapshot> findActive() {
        return snapshotRepository.findByStatus(SnapshotStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<VisitorStatsSnapshot> findImportHistory() {
        return snapshotRepository.findAllByOrderByImportedAtDesc();
    }

    /**
     * @throws IllegalStateException 적재된 행이 하나도 없는 경우.
     *                               빈 스냅샷으로 교체하면 입장객 정보가 통째로 사라진다.
     */
    @Transactional
    public VisitorStatsSnapshot activate(VisitorStatsSnapshot snapshot) {
        long rowCount = entryRepository.countBySnapshot(snapshot);

        if (rowCount == 0) {
            throw new IllegalStateException(
                    "적재된 행이 없어 활성화할 수 없습니다. version=" + snapshot.getVersion());
        }

        snapshotRepository.findByStatus(SnapshotStatus.ACTIVE)
                .filter(active -> !active.getId().equals(snapshot.getId()))
                .ifPresent(VisitorStatsSnapshot::supersede);

        snapshot.activate((int) rowCount);

        log.info("입장객통계 스냅샷 활성화: version={}, 공표월={}, 행={}",
                snapshot.getVersion(), snapshot.getPublishedMonth(), rowCount);

        return snapshot;
    }
}
