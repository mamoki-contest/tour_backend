package com.mamoki.tour.domain.relatedplace.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mamoki.tour.domain.relatedplace.entity.AlternativeCuration;

public interface AlternativeCurationRepository extends JpaRepository<AlternativeCuration, Long> {

    /** 기준 관광지 하나의 큐레이션. 건수가 적어 한 번에 읽어 메모리에서 맞춘다. */
    List<AlternativeCuration> findAllByBaseContentId(String baseContentId);
}
