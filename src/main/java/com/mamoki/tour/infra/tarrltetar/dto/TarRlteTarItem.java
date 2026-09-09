package com.mamoki.tour.infra.tarrltetar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * TarRlteTarService1 연관 장소 원본 항목.
 *
 * <p>한 행이 (기준 관광지 1곳 × 연관 장소 1곳) 이다. 같은 {@code tAtsNm} 이 연관 장소 수만큼
 * 반복해서 온다.
 *
 * <p>공급자가 KorService2 의 {@code contentId} 를 주지 않는다. {@code tAtsCd}/{@code rlteTatsCd}
 * 는 관광콘텐츠랩 자체 해시라 우리 표준 식별자와 이어지지 않는다. 그래서 매칭은 시·군 안에서
 * 정규화한 이름으로 한다. TatsCnctrRate 와 같은 제약이다.
 *
 * @param tAtsNm          기준 관광지명
 * @param rlteTatsNm      연관 장소명
 * @param rlteRegnCd      연관 장소의 법정동 시·도 코드
 * @param rlteSignguCd    연관 장소의 법정동 시·군 코드 5자리. 기준 관광지와 다른 시·군일 수 있다.
 * @param rlteCtgryLclsNm 연관 장소 대분류. 실제 응답에서 관광지 / 음식 / 숙박 세 값이 확인됐다.
 * @param rlteRank        공급자가 매긴 연관 순위. 1이 가장 가깝다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TarRlteTarItem(
        String baseYm,
        String tAtsCd,
        String tAtsNm,
        String areaCd,
        String areaNm,
        String signguCd,
        String signguNm,
        String rlteTatsCd,
        String rlteTatsNm,
        String rlteRegnCd,
        String rlteRegnNm,
        String rlteSignguCd,
        String rlteSignguNm,
        String rlteCtgryLclsNm,
        String rlteCtgryMclsNm,
        String rlteCtgrySclsNm,
        String rlteRank
) {
}
