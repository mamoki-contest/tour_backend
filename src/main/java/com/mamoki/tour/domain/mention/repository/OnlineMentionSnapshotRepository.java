package com.mamoki.tour.domain.mention.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mamoki.tour.domain.mention.entity.OnlineMentionSnapshot;
import com.mamoki.tour.global.enums.SnapshotStatus;

public interface OnlineMentionSnapshotRepository extends JpaRepository<OnlineMentionSnapshot, Long> {

    Optional<OnlineMentionSnapshot> findByStatus(SnapshotStatus status);

    Optional<OnlineMentionSnapshot> findByVersion(String version);

    List<OnlineMentionSnapshot> findAllByOrderByStartedAtDesc();

    boolean existsByVersion(String version);
}
