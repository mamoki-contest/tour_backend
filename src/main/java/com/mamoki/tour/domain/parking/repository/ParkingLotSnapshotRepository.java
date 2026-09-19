package com.mamoki.tour.domain.parking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mamoki.tour.domain.parking.entity.ParkingLotSnapshot;
import com.mamoki.tour.global.enums.SnapshotStatus;

public interface ParkingLotSnapshotRepository extends JpaRepository<ParkingLotSnapshot, Long> {

    Optional<ParkingLotSnapshot> findByStatus(SnapshotStatus status);

    List<ParkingLotSnapshot> findAllByOrderByImportedAtDesc();

    boolean existsByVersion(String version);
}
