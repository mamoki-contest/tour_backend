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

    /**
     * 같은 상태의 스냅샷이 있는지.
     *
     * <p>{@code IMPORTING} 은 진행 중인 수집을 뜻한다. {@link #findByStatus} 와 달리 여러 건이
     * 있어도 예외가 되지 않는다. 수집이 비정상 종료하면 {@code IMPORTING} 이 남을 수 있어
     * 존재 여부만 묻는 쪽이 안전하다.
     */
    boolean existsByStatus(SnapshotStatus status);
}
