package com.mamoki.tour.domain.placemapping.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mamoki.tour.domain.placemapping.entity.PlaceMapping;
import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.placemapping.enums.PlaceMappingStatus;

public interface PlaceMappingRepository extends JpaRepository<PlaceMapping, Long> {

    Optional<PlaceMapping> findBySourceAndNormalizedNameAndLawdCode(
            MappingSource source, String normalizedName, String lawdCode);

    List<PlaceMapping> findAllBySource(MappingSource source);

    /**
     * 조회에 값을 만들 수 있는 행만 읽는다.
     *
     * <p>저신뢰·미매칭 행을 함께 읽어 호출하는 쪽에서 거르게 두면, 어디 한 곳에서 거르는 것을
     * 잊는 순간 틀린 장소에 값이 붙는다. 질의에서 잘라 낸다.
     */
    @Query("""
            select m from PlaceMapping m
            where m.source = :source
              and m.status = com.mamoki.tour.domain.placemapping.enums.PlaceMappingStatus.CONFIRMED
              and m.contentId is not null
            order by m.contentId, m.normalizedName
            """)
    List<PlaceMapping> findConfirmedBySource(@Param("source") MappingSource source);

    /** 한 관광지를 가리키는 원천 쪽 표기들. 조회 시점에 매핑을 쓰는 연관 장소(#7)가 쓴다. */
    @Query("""
            select m from PlaceMapping m
            where m.source = :source
              and m.contentId = :contentId
              and m.status = com.mamoki.tour.domain.placemapping.enums.PlaceMappingStatus.CONFIRMED
            order by m.normalizedName
            """)
    List<PlaceMapping> findConfirmedBySourceAndContentId(@Param("source") MappingSource source,
                                                         @Param("contentId") String contentId);

    long countBySourceAndStatus(MappingSource source, PlaceMappingStatus status);
}
