package com.mamoki.tour.domain.search.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mamoki.tour.domain.search.entity.ThemeDefinition;

public interface ThemeDefinitionRepository extends JpaRepository<ThemeDefinition, Long> {

    Optional<ThemeDefinition> findByCode(String code);

    /** 활성 테마만, 시드가 정한 순서대로. 비활성 테마는 검색어로도 제안으로도 쓰이지 않는다. */
    List<ThemeDefinition> findAllByActiveTrueOrderBySortOrderAscCodeAsc();
}
