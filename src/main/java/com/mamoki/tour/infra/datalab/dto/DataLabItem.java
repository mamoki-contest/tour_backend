package com.mamoki.tour.infra.datalab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DataLabService 의 기초지자체 일별 방문자수 항목.
 *
 * <p>한 행은 (시·군구 1곳 × 하루 × 관광객 구분 1개) 다. 구분이 세 가지라 시·군구 하나가
 * 하루에 세 행으로 온다.
 *
 * @param signguCode 법정동 시·군구 코드 5자리. region_code 의 lawdCode 와 같은 체계다.
 *                   응답 필드명이 요청 파라미터와 달리 signguCd 가 아니라 signguCode 다.
 * @param touDivCd   관광객 구분. 1=현지인(a), 2=외지인(b), 3=외국인(c).
 * @param touNum     방문자 수. 정수가 아니라 소수점이 붙은 추정치 문자열로 온다.
 * @param baseYmd    기준 일자 yyyyMMdd.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataLabItem(
        String signguCode,
        String signguNm,
        String daywkDivCd,
        String daywkDivNm,
        String touDivCd,
        String touDivNm,
        String touNum,
        String baseYmd
) {
}
