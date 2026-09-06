package com.mamoki.tour.domain.attraction.dto;

import java.math.BigDecimal;

/**
 * 시·군 내부 중심관광지 순위 한 건.
 *
 * <p>이 순위는 같은 시·군 안에서만 의미가 있다. 시·군 사이의 절대 순위로 합치지 않는다.
 *
 * @param dataLabId 한국관광 데이터랩 식별자. 관심도 CSV 의 관광지ID 와 같은 체계다.
 */
public record CenterRank(
        String dataLabId,
        String name,
        String normalizedName,
        BigDecimal latitude,
        BigDecimal longitude,
        String lawdCode,
        int rank,
        String baseYm
) {
}
