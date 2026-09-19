package com.mamoki.tour.domain.parking.importer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 전국주차장정보표준데이터의 강원 행 하나.
 *
 * <p>파일의 34열 중 이 서비스가 쓰는 항목만 담는다. 요금 상세(기본시간·단위요금·월정기권)와
 * 결제방법·특기사항·전화번호는 상세 응답에서 쓰지 않아 옮기지 않는다.
 *
 * <p>좌표는 비어 있을 수 있다. 0 으로 채우지 않고 null 로 둔다. 좌표가 없는 주차장은
 * 관광지 반경 조회에서 빠지지만 적재는 된다.
 *
 * @param capacity      주차구획수. 0 도 실제 값이다
 * @param dataBaseDate  공급자가 밝힌 데이터 기준일. 우리가 적재한 날과 다르다
 */
public record ParkingCatalogRow(
        String managementNumber,
        String name,
        String category,
        String parkingType,
        String roadAddress,
        String lotAddress,
        int capacity,
        String operatingDays,
        String weekdayOpenTime,
        String weekdayCloseTime,
        String saturdayOpenTime,
        String saturdayCloseTime,
        String holidayOpenTime,
        String holidayCloseTime,
        String feeInfo,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDate dataBaseDate,
        String providerCode,
        String providerName
) {

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}
