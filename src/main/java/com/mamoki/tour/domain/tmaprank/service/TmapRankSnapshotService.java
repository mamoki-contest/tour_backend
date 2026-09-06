package com.mamoki.tour.domain.tmaprank.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.tmaprank.entity.TmapRankSnapshot;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankEntryRepository;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankSnapshotRepository;
import com.mamoki.tour.global.enums.SnapshotStatus;

/**
 * TMAP 검색순위 스냅샷의 활성화와 이력 관리.
 *
 * <p>활성 스냅샷 교체는 한 트랜잭션 안에서 일어난다. 새 스냅샷을 활성화하는 순간에만
 * 직전 스냅샷이 물러나므로, 조회 쪽에서 활성 스냅샷이 잠깐 사라지는 구간이 없다.
 *
 * <p>적재에 실패하면 새 스냅샷만 FAILED 로 남고 직전 정상 스냅샷은 그대로 활성 상태를 지킨다.
 */
@Service
public class TmapRankSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(TmapRankSnapshotService.class);

    private final TmapRankSnapshotRepository snapshotRepository;
    private final TmapRankEntryRepository tmapRankEntryRepository;

    public TmapRankSnapshotService(TmapRankSnapshotRepository snapshotRepository,
                                   TmapRankEntryRepository tmapRankEntryRepository) {
        this.snapshotRepository = snapshotRepository;
        this.tmapRankEntryRepository = tmapRankEntryRepository;
    }

    @Transactional(readOnly = true)
    public Optional<TmapRankSnapshot> findActive() {
        return snapshotRepository.findByStatus(SnapshotStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<TmapRankSnapshot> findImportHistory() {
        return snapshotRepository.findAllByOrderByImportedAtDesc();
    }

    /**
     * 검증을 모두 통과한 스냅샷을 활성화하고 직전 활성 스냅샷을 물러나게 한다.
     *
     * @throws IllegalStateException 적재된 순위 행이 하나도 없는 경우.
     *                               빈 스냅샷으로 교체하면 순위 정보가 통째로 사라지므로 막는다.
     */
    @Transactional
    public TmapRankSnapshot activate(TmapRankSnapshot snapshot) {
        long rowCount = tmapRankEntryRepository.countBySnapshot(snapshot);

        if (rowCount == 0) {
            throw new IllegalStateException(
                    "적재된 순위 행이 없어 활성화할 수 없습니다. version=" + snapshot.getVersion());
        }

        snapshotRepository.findByStatus(SnapshotStatus.ACTIVE)
                .filter(active -> !active.equals(snapshot))
                .ifPresent(active -> {
                    log.info("TMAP 검색순위 스냅샷 교체: {} -> {}", active.getVersion(), snapshot.getVersion());
                    active.supersede();
                });

        snapshot.activate((int) rowCount);
        return snapshot;
    }

    /**
     * 적재 실패를 이력으로 남긴다. 직전 활성 스냅샷은 건드리지 않는다.
     */
    @Transactional
    public TmapRankSnapshot markFailed(TmapRankSnapshot snapshot, String reason) {
        log.warn("TMAP 검색순위 스냅샷 적재 실패: version={}, reason={}", snapshot.getVersion(), reason);

        tmapRankEntryRepository.deleteBySnapshot(snapshot);
        snapshot.fail(reason);

        return snapshot;
    }
}
