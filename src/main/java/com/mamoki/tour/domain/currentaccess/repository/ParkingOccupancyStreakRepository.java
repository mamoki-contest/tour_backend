package com.mamoki.tour.domain.currentaccess.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mamoki.tour.domain.currentaccess.entity.ParkingOccupancyStreak;

public interface ParkingOccupancyStreakRepository extends JpaRepository<ParkingOccupancyStreak, Long> {

    List<ParkingOccupancyStreak> findByPrkIdIn(Collection<String> prkIds);
}
