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
     * 스냅샷에 담긴 항목을 상태와 무관하게 조회한다.
     *
     * <p>결과에 없는 관광지는 이 스냅샷이 아예 보지 않은 곳이며, 언급이 적은 것이 아니다.
     *
     * <p>예전에는 여기서 {@code COLLECTED} 만 걸러 왔다(#94). 그러면 모호(AMBIGUOUS)·
     * 대상 아님(UNAVAILABLE)·수집 실패가 전부 "결과에 없음" 한 가지로 합쳐져, 위쪽에서는
     * 왜 값이 없는지를 되살릴 방법이 없었다. 판정 자체는 수집이 이미 내려 두었으므로
     * 그대로 올려보내고, 정렬에 쓸 수 있는지는 상태를 받아 본 쪽이 가린다
     * ({@link OnlineMentionEntry#isSortable()}).
     */
    @Query("""
            select e from OnlineMentionEntry e
            where e.snapshot = :snapshot
              and e.contentId in :contentIds
            """)
    List<OnlineMentionEntry> findByContentIds(
            @Param("snapshot") OnlineMentionSnapshot snapshot,
            @Param("contentIds") Collection<String> contentIds);

    long countBySnapshot(OnlineMentionSnapshot snapshot);

    void deleteBySnapshot(OnlineMentionSnapshot snapshot);
}
