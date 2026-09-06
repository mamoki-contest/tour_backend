package com.mamoki.tour.domain.mention.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.mention.entity.OnlineMentionEntry;
import com.mamoki.tour.domain.mention.entity.OnlineMentionSnapshot;
import com.mamoki.tour.domain.mention.repository.OnlineMentionEntryRepository;
import com.mamoki.tour.domain.mention.repository.OnlineMentionSnapshotRepository;
import com.mamoki.tour.global.enums.SnapshotStatus;

/**
 * 수집 결과를 기록한다.
 *
 * <p>수집은 관광지 수만큼 외부 호출이 이어져 오래 걸린다. 한 트랜잭션으로 묶으면 커넥션을
 * 그동안 붙잡고 있어야 하므로, 시작·진행·마무리를 나누어 커밋한다.
 *
 * <p>진행 중 커밋된 행은 스냅샷이 활성화되기 전까지 조회에 쓰이지 않는다. 활성 스냅샷만
 * 조회하기 때문이다.
 */
@Service
public class OnlineMentionSnapshotWriter {

    private final OnlineMentionSnapshotRepository snapshotRepository;
    private final OnlineMentionEntryRepository entryRepository;

    public OnlineMentionSnapshotWriter(OnlineMentionSnapshotRepository snapshotRepository,
                                       OnlineMentionEntryRepository entryRepository) {
        this.snapshotRepository = snapshotRepository;
        this.entryRepository = entryRepository;
    }

    /** 시작 기록은 즉시 커밋한다. 이후 실패해도 시도했다는 사실이 이력에 남아야 한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OnlineMentionSnapshot start(String collectionMonth, String queryRuleVersion, int targetCount) {
        return snapshotRepository.save(OnlineMentionSnapshot.builder()
                .version(nextVersion(collectionMonth, queryRuleVersion))
                .collectionMonth(collectionMonth)
                .queryRuleVersion(queryRuleVersion)
                .startedAt(LocalDateTime.now())
                .status(SnapshotStatus.IMPORTING)
                .targetCount(targetCount)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveEntries(List<OnlineMentionEntry> entries) {
        entryRepository.saveAll(entries);
    }

    /**
     * 수집이 모두 끝났을 때만 활성화한다. 직전 활성 스냅샷은 같은 트랜잭션에서 물러난다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OnlineMentionSnapshot activate(Long snapshotId, int collectedCount) {
        OnlineMentionSnapshot snapshot = snapshotRepository.findById(snapshotId).orElseThrow();

        snapshotRepository.findByStatus(SnapshotStatus.ACTIVE)
                .filter(active -> !active.getId().equals(snapshotId))
                .ifPresent(OnlineMentionSnapshot::supersede);

        snapshot.complete(collectedCount, LocalDateTime.now());
        return snapshot;
    }

    /**
     * 실패를 이력에 남기고 부분 수집분을 지운다. 직전 활성 스냅샷은 건드리지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OnlineMentionSnapshot fail(Long snapshotId, String reason, int collectedCount) {
        OnlineMentionSnapshot snapshot = snapshotRepository.findById(snapshotId).orElseThrow();

        entryRepository.deleteBySnapshot(snapshot);
        snapshot.fail(truncate(reason), collectedCount, LocalDateTime.now());

        return snapshot;
    }

    private String nextVersion(String collectionMonth, String queryRuleVersion) {
        String base = collectionMonth + "_" + queryRuleVersion;

        if (!snapshotRepository.existsByVersion(base)) {
            return base;
        }

        for (int sequence = 2; sequence < 1000; sequence++) {
            String candidate = base + "-" + sequence;

            if (!snapshotRepository.existsByVersion(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("같은 달 수집 횟수가 너무 많습니다: " + base);
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return "알 수 없는 오류";
        }

        return reason.length() <= 500 ? reason : reason.substring(0, 497) + "...";
    }
}
