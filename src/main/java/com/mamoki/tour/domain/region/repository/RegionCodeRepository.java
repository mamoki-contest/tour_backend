package com.mamoki.tour.domain.region.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mamoki.tour.domain.region.entity.RegionCode;

public interface RegionCodeRepository extends JpaRepository<RegionCode, Long> {

    Optional<RegionCode> findByLawdCode(String lawdCode);

    List<RegionCode> findAllByAreaCode(String areaCode);

    Optional<RegionCode> findByAreaCodeAndSigunguCode(String areaCode, String sigunguCode);
}
