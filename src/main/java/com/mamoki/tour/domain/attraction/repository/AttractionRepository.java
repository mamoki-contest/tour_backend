package com.mamoki.tour.domain.attraction.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mamoki.tour.domain.attraction.entity.Attraction;

public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    Optional<Attraction> findByContentId(String contentId);

    /** 개인 컬렉션 재조회(#9)가 사용하는 배치 조회. */
    List<Attraction> findAllByContentIdIn(Collection<String> contentIds);

    List<Attraction> findAllByRegionCodeLawdCode(String lawdCode);

    /** 지도 경계(bbox) 조회. 좌표가 없는 장소는 결과에서 제외된다. */
    List<Attraction> findAllByLatitudeBetweenAndLongitudeBetween(
            BigDecimal minLatitude, BigDecimal maxLatitude,
            BigDecimal minLongitude, BigDecimal maxLongitude);
}
