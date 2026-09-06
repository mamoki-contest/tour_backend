package com.mamoki.tour.infra.korservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * KorService2 목록 항목 원본.
 *
 * <p>공급자는 값이 없을 때 null 이 아니라 빈 문자열을 내려준다. 빈 문자열을 그대로 두면
 * `값이 있다`고 오해하게 되므로, 표준 계약으로 변환할 때 null 로 정규화한다.
 *
 * @param lDongRegnCd   법정동 시·도 코드 (강원특별자치도 51)
 * @param lDongSignguCd 법정동 시·군 코드 3자리
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KorServiceItem(
        String contentid,
        String contenttypeid,
        String title,
        String addr1,
        String addr2,
        String firstimage,
        String firstimage2,
        String mapx,
        String mapy,
        String areacode,
        String sigungucode,
        String lDongRegnCd,
        String lDongSignguCd,
        String cat1,
        String modifiedtime
) {
}
