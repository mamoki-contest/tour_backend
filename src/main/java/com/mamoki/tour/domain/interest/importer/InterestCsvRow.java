package com.mamoki.tour.domain.interest.importer;

import java.math.BigDecimal;

/**
 * 관심도 CSV 한 행.
 *
 * @param dataLabId 한국관광 데이터랩 식별자. LocgoHubTarService1 의 hubTatsCd 와 같은 체계다.
 * @param ageGroup  연령대. {@code 전체} 또는 {@code 20}~{@code 60}.
 * @param ratio     해당 지역 안에서의 비중(%). 시·군을 넘어 비교하지 않는다.
 */
public record InterestCsvRow(
        int rank,
        String dataLabId,
        String placeName,
        String category,
        String ageGroup,
        BigDecimal ratio
) {

    public static final String AGE_GROUP_ALL = "전체";

    public boolean isAllAges() {
        return AGE_GROUP_ALL.equals(ageGroup);
    }
}
