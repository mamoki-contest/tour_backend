package com.mamoki.tour.domain.mention.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mamoki.tour.domain.mention.entity.OnlineMentionEntry;
import com.mamoki.tour.domain.mention.entity.OnlineMentionSnapshot;

public interface OnlineMentionEntryRepository extends JpaRepository<OnlineMentionEntry, Long> {

    /**
     * 활성 스냅샷에서 정렬 가능한 값만 조회한다.
     * 결과에 없는 관광지는 모호하거나 수집되지 않은 것이며, 언급이 적은 것이 아니다.
     */
    @Query("""
            select e from OnlineMentionEntry e
            where e.snapshot = :snapshot
              and e.status = com.mamoki.tour.global.enums.MentionStatus.COLLECTED
              and e.mentionTotal is not null
              and e.contentId in :contentIds
            """)
    List<OnlineMentionEntry> findSortableByContentIds(
            @Param("snapshot") OnlineMentionSnapshot snapshot,
            @Param("contentIds") Collection<String> contentIds);

    long countBySnapshot(OnlineMentionSnapshot snapshot);

    void deleteBySnapshot(OnlineMentionSnapshot snapshot);
}
