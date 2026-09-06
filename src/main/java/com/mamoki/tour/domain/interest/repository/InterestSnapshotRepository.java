package com.mamoki.tour.domain.interest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mamoki.tour.domain.interest.entity.InterestSnapshot;
import com.mamoki.tour.global.enums.SnapshotStatus;

public interface InterestSnapshotRepository extends JpaRepository<InterestSnapshot, Long> {

    Optional<InterestSnapshot> findByStatus(SnapshotStatus status);

    Optional<InterestSnapshot> findByVersion(String version);

    List<InterestSnapshot> findAllByOrderByImportedAtDesc();

    boolean existsByVersion(String version);
}
