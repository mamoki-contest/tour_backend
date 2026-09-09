package com.mamoki.tour.infra.korservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * KorService2 목록 항목 원본.
 *
 * <p>공급자는 값이 없을 때 null 이 아니라 빈 문자열을 내려준다. 빈 문자열을 그대로 두면
 * `값이 있다`고 오해하게 되므로, 표준 계약으로 변환할 때 null 로 정규화한다.
 *
 * <p>목록({@code areaBasedList2})과 상세({@code detailCommon2})가 같은 항목 구조를 쓴다.
 * 상세에서만 채워지는 필드는 목록 응답에서 아예 오지 않으며, 그 경우 null 로 남는다.
 *
 * @param lDongRegnCd   법정동 시·도 코드 (강원특별자치도 51)
 * @param lDongSignguCd 법정동 시·군 코드 3자리
 * @param tel           전화번호. 상세 전용이며 값이 없으면 빈 문자열로 온다.
 * @param homepage      홈페이지. 공급자가 앵커 태그를 통째로 넣어 주기도 한다.
 * @param overview      개요 설명. 상세 전용.
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
        String modifiedtime,
        String zipcode,
        String tel,
        String homepage,
        String overview
) {
}
