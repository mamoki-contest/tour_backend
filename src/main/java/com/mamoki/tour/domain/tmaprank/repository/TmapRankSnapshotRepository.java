package com.mamoki.tour.domain.tmaprank.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mamoki.tour.domain.tmaprank.entity.TmapRankSnapshot;
import com.mamoki.tour.global.enums.SnapshotStatus;

public interface TmapRankSnapshotRepository extends JpaRepository<TmapRankSnapshot, Long> {

    Optional<TmapRankSnapshot> findByStatus(SnapshotStatus status);

    Optional<TmapRankSnapshot> findByVersion(String version);

    List<TmapRankSnapshot> findAllByOrderByImportedAtDesc();

    boolean existsByVersion(String version);
}
