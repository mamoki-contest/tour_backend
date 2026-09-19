package com.mamoki.tour.domain.placemapping.support;

import java.math.BigDecimal;

import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;

/**
 * 원천 이름을 이어 붙일 수 있는 카탈로그 후보 한 곳.
 *
 * <p>판정이 순수 계산으로 남도록 엔티티 대신 이 값만 넘긴다. 좌표는 결측일 수 있으며
 * 없는 좌표를 0 으로 채우지 않는다. 적도 앞바다로 옮겨 놓는 셈이 된다.
 */
public record CatalogCandidate(
        String contentId,
        String name,
        BigDecimal latitude,
        BigDecimal longitude
) {

    public String normalizedName() {
        return PlaceNameNormalizer.normalize(name);
    }

    public boolean hasCoordinate() {
        return latitude != null && longitude != null;
    }
}
