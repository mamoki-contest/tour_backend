package com.mamoki.tour.domain.visittiming.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 한 장소의 향후 30일 집중률 예측 묶음.
 *
 * <p>공급자가 관광지 식별자를 주지 않으므로 이름으로 이어 붙인다. 표기 차이를 흡수한
 * {@code normalizedName} 이 매칭 키이고, 좌표가 양쪽에 있으면 같은 장소인지 한 번 더 확인한다.
 *
 * @param lawdCode 법정동 시·군 코드 5자리
 * @param days     날짜 오름차순으로 정렬된 일별 예측. 값이 없는 날은 rate 가 null 이다.
 */
public record AttractionForecast(
        String name,
        String normalizedName,
        String lawdCode,
        BigDecimal latitude,
        BigDecimal longitude,
        List<DailyConcentration> days
) {
}
