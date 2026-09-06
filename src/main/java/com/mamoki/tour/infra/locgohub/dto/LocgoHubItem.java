package com.mamoki.tour.infra.locgohub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LocgoHubTarService1 의 시·군 중심관광지 항목.
 *
 * <p>hubTatsCd 는 한국관광 데이터랩의 자체 식별자다. KorService2 의 contentId 와 체계가
 * 다르므로 그대로 매칭할 수 없다. 다만 관심도 CSV(#4)의 관광지ID 와는 같은 체계라
 * 그쪽과는 식별자로 직접 이어붙일 수 있다.
 *
 * @param signguCd 법정동 시·군 코드 5자리. KorService2 의 sigunguCode 와 체계가 다르다.
 * @param hubRank  시·군 내부 중심관광지 순위. 시·군 사이의 절대 순위가 아니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LocgoHubItem(
        String baseYm,
        String mapX,
        String mapY,
        String areaCd,
        String areaNm,
        String signguCd,
        String signguNm,
        String hubTatsCd,
        String hubTatsNm,
        String hubCtgryLclsNm,
        String hubCtgryMclsNm,
        String hubRank
) {
}
