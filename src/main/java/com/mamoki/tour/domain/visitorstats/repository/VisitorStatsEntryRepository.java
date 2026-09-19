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

    /**
     * 카탈로그에 잇지 못한 행. 장소 매핑 배치(#55)가 이 이름들만 카카오로 찾는다.
     *
     * <p>이미 이어진 행까지 부르면 얻는 것 없이 하루 한도만 깎는다.
     */
    @Query("""
            select e from VisitorStatsEntry e
            where e.snapshot = :snapshot
              and e.matchStatus = com.mamoki.tour.global.enums.CatalogMatchStatus.UNMATCHED
            """)
    List<VisitorStatsEntry> findUnmatchedBySnapshot(@Param("snapshot") VisitorStatsSnapshot snapshot);

    /**
     * 활성 스냅샷에서 확정 매칭된 관광지 식별자를 행마다 하나씩 가져온다.
     *
     * <p>장소 매핑 배치(#72)가 매칭률의 분자를 세고, 이미 값이 붙은 관광지에 두 번째 행을
     * 잇지 않으려고 쓴다. 한 관광지에 두 행이 붙으면 조회가 둘 중 아무거나 보여 준다.
     */
    @Query("""
            select e.contentId from VisitorStatsEntry e
            where e.snapshot = :snapshot
              and e.matchStatus = com.mamoki.tour.global.enums.CatalogMatchStatus.MATCHED
            """)
    List<String> findMatchedContentIds(@Param("snapshot") VisitorStatsSnapshot snapshot);

    long countBySnapshot(VisitorStatsSnapshot snapshot);

    void deleteBySnapshot(VisitorStatsSnapshot snapshot);
}
