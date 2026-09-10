package com.mamoki.tour.domain.attraction.support;

import java.math.BigDecimal;

/**
 * 지도에서 확정한 조회 경계.
 *
 * <p>경계 안인지 판단할 수 없는 장소, 즉 좌표가 없는 장소는 담지 않는다. 지도 화면에 찍을
 * 수 없는 항목을 목록에만 남기면 어디에 있는지 확인할 방법이 없다.
 */
public record MapBounds(BigDecimal minLatitude, BigDecimal maxLatitude,
                        BigDecimal minLongitude, BigDecimal maxLongitude) {

    public boolean contains(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }

        return minLatitude.compareTo(latitude) <= 0
                && maxLatitude.compareTo(latitude) >= 0
                && minLongitude.compareTo(longitude) <= 0
                && maxLongitude.compareTo(longitude) >= 0;
    }
}
