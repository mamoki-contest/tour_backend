package com.mamoki.tour.domain.tmaprank.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mamoki.tour.domain.tmaprank.entity.TmapRankEntry;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankSnapshot;

public interface TmapRankEntryRepository extends JpaRepository<TmapRankEntry, Long> {

    /**
     * 활성 스냅샷에서 주어진 관광지들의 TMAP 검색순위를 조회한다.
     * 확정 매칭된 행만 반환하므로, 결과에 없는 관광지는 순위 미수록이다.
     */
    @Query("""
            select ai from TmapRankEntry ai
            where ai.snapshot = :snapshot
              and ai.matchStatus = com.mamoki.tour.global.enums.CatalogMatchStatus.MATCHED
              and ai.contentId in :contentIds
            """)
    List<TmapRankEntry> findMatchedByContentIds(@Param("snapshot") TmapRankSnapshot snapshot,
                                                     @Param("contentIds") Collection<String> contentIds);

    /**
     * 활성 스냅샷에서 확정 매칭된 관광지 식별자를 모두 가져온다.
     *
     * <p>언급량 수집이 카탈로그 전체를 돌면서 장소마다 순위 수록 여부를 물어야 해서, 식별자만
     * 한 번에 받아 메모리에서 확인한다. 장소마다 조회하면 카탈로그 크기만큼 질의가 늘어난다.
     */
    @Query("""
            select ai.contentId from TmapRankEntry ai
            where ai.snapshot = :snapshot
              and ai.matchStatus = com.mamoki.tour.global.enums.CatalogMatchStatus.MATCHED
            """)
    List<String> findMatchedContentIds(@Param("snapshot") TmapRankSnapshot snapshot);

    long countBySnapshot(TmapRankSnapshot snapshot);

    void deleteBySnapshot(TmapRankSnapshot snapshot);
}
