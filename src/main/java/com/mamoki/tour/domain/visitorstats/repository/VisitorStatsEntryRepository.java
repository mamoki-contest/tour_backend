package com.mamoki.tour.domain.visitorstats.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mamoki.tour.domain.visitorstats.entity.VisitorStatsEntry;
import com.mamoki.tour.domain.visitorstats.entity.VisitorStatsSnapshot;

public interface VisitorStatsEntryRepository extends JpaRepository<VisitorStatsEntry, Long> {

    /**
     * 활성 스냅샷에서 주어진 관광지들의 입장객 수를 조회한다.
     * 확정 매칭된 행만 반환하므로, 결과에 없는 관광지는 통계 미등록이거나 공표월 미집계다.
     */
    @Query("""
            select e from VisitorStatsEntry e
            where e.snapshot = :snapshot
              and e.matchStatus = com.mamoki.tour.global.enums.CatalogMatchStatus.MATCHED
              and e.contentId in :contentIds
            """)
    List<VisitorStatsEntry> findMatchedByContentIds(@Param("snapshot") VisitorStatsSnapshot snapshot,
                                                    @Param("contentIds") Collection<String> contentIds);

    long countBySnapshot(VisitorStatsSnapshot snapshot);

    void deleteBySnapshot(VisitorStatsSnapshot snapshot);
}
