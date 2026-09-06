package com.mamoki.tour.domain.attraction.support;

import java.math.BigDecimal;

/**
 * 두 지점 사이의 거리 계산.
 *
 * <p>공급자 간 매칭에서 같은 이름이 실제로 같은 장소인지 확인하는 데만 쓴다.
 * 정밀한 거리 계산이 목적이 아니므로 지구를 구로 근사한다.
 */
public final class Coordinates {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private Coordinates() {
    }

    /**
     * @return 두 지점 사이 거리(m). 좌표가 하나라도 없으면 판단할 수 없으므로 null.
     */
    public static Double distanceMeters(BigDecimal lat1, BigDecimal lon1,
                                        BigDecimal lat2, BigDecimal lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return null;
        }

        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double deltaPhi = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double deltaLambda = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);

        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
