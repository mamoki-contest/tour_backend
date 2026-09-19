package com.mamoki.tour.domain.parking.importer;

import com.mamoki.tour.domain.parking.entity.ParkingLotSnapshot;

/**
 * 주차장 표준데이터 한 번 적재의 결과.
 *
 * @param totalRows      적재한 강원 주차장 수
 * @param withCoordinates 좌표가 있어 관광지 반경 조회에 쓸 수 있는 수
 * @param districtCount  적재된 시·군 수. 18 이어야 강원 전부다
 */
public record ParkingCatalogImportResult(
        ParkingLotSnapshot snapshot,
        int totalRows,
        int withCoordinates,
        int districtCount
) {

    /** 좌표가 없어 반경 조회에서 빠지는 수. 버린 것이 아니라 적재는 되어 있다. */
    public int withoutCoordinates() {
        return totalRows - withCoordinates;
    }
}
