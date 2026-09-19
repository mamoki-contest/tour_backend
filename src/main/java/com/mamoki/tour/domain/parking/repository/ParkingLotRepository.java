package com.mamoki.tour.domain.parking.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mamoki.tour.domain.parking.entity.ParkingLot;
import com.mamoki.tour.domain.parking.entity.ParkingLotSnapshot;

public interface ParkingLotRepository extends JpaRepository<ParkingLot, Long> {

    /**
     * 좌표 사각형 안의 주차장을 찾는다.
     *
     * <p>사각형으로 먼저 좁히고 정확한 거리는 호출부가 계산한다. DB 에서 삼각함수를 돌리면
     * 인덱스를 쓰지 못한다. 좌표가 없는 행은 어디에 있는지 알 수 없으므로 애초에 들어오지 않는다.
     */
    @Query("""
            select l from ParkingLot l
            where l.snapshot = :snapshot
              and l.latitude between :minLatitude and :maxLatitude
              and l.longitude between :minLongitude and :maxLongitude
            """)
    List<ParkingLot> findWithinBox(@Param("snapshot") ParkingLotSnapshot snapshot,
                                   @Param("minLatitude") BigDecimal minLatitude,
                                   @Param("maxLatitude") BigDecimal maxLatitude,
                                   @Param("minLongitude") BigDecimal minLongitude,
                                   @Param("maxLongitude") BigDecimal maxLongitude);

    long countBySnapshot(ParkingLotSnapshot snapshot);

    void deleteBySnapshot(ParkingLotSnapshot snapshot);
}
