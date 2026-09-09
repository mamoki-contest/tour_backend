package com.mamoki.tour.domain.relatedplace.dto;

import com.mamoki.tour.domain.relatedplace.enums.RelatedPlaceKind;

/**
 * 공급자 연관 장소 한 행을 표준 계약으로 변환한 결과.
 *
 * <p>공급자가 표준 관광지 식별자를 주지 않아 이름이 매칭 키다. 기준 관광지도, 연관 장소도
 * 정규화한 이름으로 이어 붙인다.
 *
 * @param baseNormalizedName 기준 관광지명을 정규화한 값. 어느 관광지의 연관 목록인지 가른다.
 * @param normalizedName     연관 장소명을 정규화한 값. 원래 장소와 같은 곳인지 가르는 데 쓴다.
 * @param lawdCode           연관 장소의 법정동 시·군 코드 5자리. 기준 관광지와 다를 수 있다.
 * @param rank               공급자가 매긴 연관 순위. 값이 없거나 숫자가 아니면 null 이며 0 으로 채우지 않는다.
 */
public record RelatedPlaceRow(
        String baseName,
        String baseNormalizedName,
        String name,
        String normalizedName,
        RelatedPlaceKind kind,
        String categoryLarge,
        String categoryMiddle,
        String categorySmall,
        String lawdCode,
        String regionName,
        Integer rank
) {
}
