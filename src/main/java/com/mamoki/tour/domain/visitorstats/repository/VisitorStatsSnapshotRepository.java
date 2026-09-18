package com.mamoki.tour.domain.visitorstats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mamoki.tour.domain.visitorstats.entity.VisitorStatsSnapshot;
import com.mamoki.tour.global.enums.SnapshotStatus;

public interface VisitorStatsSnapshotRepository extends JpaRepository<VisitorStatsSnapshot, Long> {

    Optional<VisitorStatsSnapshot> findByStatus(SnapshotStatus status);

    List<VisitorStatsSnapshot> findAllByOrderByImportedAtDesc();

    boolean existsByVersion(String version);
}
