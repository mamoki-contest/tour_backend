package com.mamoki.tour.domain.interest.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mamoki.tour.domain.interest.entity.AttractionInterest;
import com.mamoki.tour.domain.interest.entity.InterestSnapshot;

public interface AttractionInterestRepository extends JpaRepository<AttractionInterest, Long> {

    /**
     * 활성 스냅샷에서 주어진 관광지들의 관심도를 조회한다.
     * 확정 매칭된 행만 반환하므로, 결과에 없는 관광지는 관심도 미산정이다.
     */
    @Query("""
            select ai from AttractionInterest ai
            where ai.snapshot = :snapshot
              and ai.matchStatus = com.mamoki.tour.global.enums.InterestMatchStatus.MATCHED
              and ai.contentId in :contentIds
            """)
    List<AttractionInterest> findMatchedByContentIds(@Param("snapshot") InterestSnapshot snapshot,
                                                     @Param("contentIds") Collection<String> contentIds);

    long countBySnapshot(InterestSnapshot snapshot);

    void deleteBySnapshot(InterestSnapshot snapshot);
}
