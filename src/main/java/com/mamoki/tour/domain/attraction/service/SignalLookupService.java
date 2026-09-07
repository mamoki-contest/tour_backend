package com.mamoki.tour.domain.attraction.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.attraction.dto.OnlineMentionView;
import com.mamoki.tour.domain.attraction.dto.TmapRankView;
import com.mamoki.tour.domain.mention.entity.OnlineMentionEntry;
import com.mamoki.tour.domain.mention.entity.OnlineMentionSnapshot;
import com.mamoki.tour.domain.mention.repository.OnlineMentionEntryRepository;
import com.mamoki.tour.domain.mention.repository.OnlineMentionSnapshotRepository;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankEntry;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankSnapshot;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankEntryRepository;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankSnapshotRepository;
import com.mamoki.tour.global.enums.SnapshotStatus;
import com.mamoki.tour.global.enums.TmapRankStatus;

/**
 * 활성 스냅샷에서 관광지별 신호를 찾아온다.
 *
 * <p>신호마다 원천과 기준 시점이 다르므로 각각 독립된 값으로 돌려준다. 하나의 점수로 합치지 않는다.
 *
 * <p>스냅샷에 없는 관광지는 값을 만들어내지 않는다. 없다는 사실 자체가 상태로 전달된다.
 */
@Service
public class SignalLookupService {

    private final OnlineMentionSnapshotRepository mentionSnapshotRepository;
    private final OnlineMentionEntryRepository mentionEntryRepository;
    private final TmapRankSnapshotRepository tmapSnapshotRepository;
    private final TmapRankEntryRepository tmapEntryRepository;

    public SignalLookupService(OnlineMentionSnapshotRepository mentionSnapshotRepository,
                               OnlineMentionEntryRepository mentionEntryRepository,
                               TmapRankSnapshotRepository tmapSnapshotRepository,
                               TmapRankEntryRepository tmapEntryRepository) {
        this.mentionSnapshotRepository = mentionSnapshotRepository;
        this.mentionEntryRepository = mentionEntryRepository;
        this.tmapSnapshotRepository = tmapSnapshotRepository;
        this.tmapEntryRepository = tmapEntryRepository;
    }

    /**
     * 활성 온라인 언급량 스냅샷에서 값을 찾는다.
     *
     * @return 스냅샷이 아직 없으면 빈 값. 정렬을 적용할 수 없다는 뜻이다.
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, OnlineMentionView>> findOnlineMentions(Collection<String> contentIds) {
        Optional<OnlineMentionSnapshot> active =
                mentionSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE);

        if (active.isEmpty() || contentIds.isEmpty()) {
            return active.map(snapshot -> Map.of());
        }

        OnlineMentionSnapshot snapshot = active.get();
        Map<String, OnlineMentionView> views = new HashMap<>();

        for (OnlineMentionEntry entry
                : mentionEntryRepository.findSortableByContentIds(snapshot, contentIds)) {

            views.put(entry.getContentId(), new OnlineMentionView(
                    entry.getStatus(),
                    entry.getMentionTotal(),
                    entry.getCollectedAt(),
                    snapshot.getQueryRuleVersion()));
        }

        return Optional.of(views);
    }

    /** 활성 온라인 언급량 스냅샷의 검색어 규칙 버전. 스냅샷이 없으면 빈 값. */
    @Transactional(readOnly = true)
    public Optional<String> findMentionRuleVersion() {
        return mentionSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE)
                .map(OnlineMentionSnapshot::getQueryRuleVersion);
    }

    /**
     * 활성 TMAP 스냅샷에서 시·군 내 순위를 찾는다. 수록되지 않은 장소는 결과에 담기지 않는다.
     */
    @Transactional(readOnly = true)
    public Map<String, TmapRankView> findTmapRanks(Collection<String> contentIds) {
        Optional<TmapRankSnapshot> active =
                tmapSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE);

        if (active.isEmpty() || contentIds.isEmpty()) {
            return Map.of();
        }

        TmapRankSnapshot snapshot = active.get();
        Map<String, TmapRankView> views = new HashMap<>();

        for (TmapRankEntry entry
                : tmapEntryRepository.findMatchedByContentIds(snapshot, contentIds)) {

            views.put(entry.getContentId(), new TmapRankView(
                    TmapRankStatus.AVAILABLE,
                    entry.getSourceRank(),
                    snapshot.getSourcePeriod()));
        }

        return views;
    }
}
