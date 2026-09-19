package com.mamoki.tour.domain.relatedplace.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.mamoki.tour.global.enums.DataStatus;

/**
 * 시·군 하나의 연관 장소와 그 데이터 상태.
 *
 * <p>공급자 조회 단위가 시·군이라 한 번 받은 것을 이 단위로 들고 다닌다. 상세 조회(#7)와
 * 장소 매핑 배치(#55)가 같은 응답을 쓰기 때문에 둘이 공유한다.
 */
public record RegionRelatedPlaces(List<RelatedPlaceRow> rows, DataStatus dataStatus,
                                  LocalDateTime collectedAt) {

    public static RegionRelatedPlaces empty() {
        return new RegionRelatedPlaces(List.of(), DataStatus.NO_DATA, null);
    }

    public static RegionRelatedPlaces of(List<RelatedPlaceRow> rows, DataStatus dataStatus,
                                         LocalDateTime collectedAt) {
        return new RegionRelatedPlaces(List.copyOf(rows), dataStatus, collectedAt);
    }

    /**
     * 기준 관광지를 가리키는 이름들 중 어느 하나로 묶인 행.
     *
     * <p>이름이 여럿인 것은 공급자가 같은 장소를 다르게 적기 때문이다. 카탈로그 이름으로
     * 먼저 찾고, 매핑 표가 알려 준 원천 쪽 이름이 그 뒤를 잇는다.
     */
    public List<RelatedPlaceRow> rowsOfAny(Set<String> baseNormalizedNames) {
        if (baseNormalizedNames.isEmpty()) {
            return List.of();
        }

        return rows.stream()
                .filter(row -> baseNormalizedNames.contains(row.baseNormalizedName()))
                .toList();
    }
}
