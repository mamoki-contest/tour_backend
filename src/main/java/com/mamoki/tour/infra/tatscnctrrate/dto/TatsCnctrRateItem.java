package com.mamoki.tour.infra.tatscnctrrate.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * TatsCnctrRateService 의 관광지 집중률 예측 항목.
 *
 * <p>한 행이 "관광지 1곳 × 날짜 1일" 이다. 한 번 호출에 시·군 안의 관광지마다
 * 향후 30일치 행이 함께 내려온다.
 *
 * <p>공급자는 관광지 식별자를 주지 않고 이름({@code tAtsNm})만 준다. KorService2 의
 * contentId 와 이어 붙이려면 이름 정규화 매칭을 거쳐야 한다.
 *
 * @param baseYmd    예측 대상 날짜 yyyyMMdd. 요청 파라미터가 아니라 응답 필드다.
 * @param signguCd   법정동 시·군 코드 5자리. KorService2 의 sigunguCode 와 체계가 다르다.
 * @param cnctrRate  집중률 예측값. 공급자가 공식 등급 기준을 제공하지 않으므로
 *                   장소 사이의 절대 비교에 쓰지 않고 같은 장소의 30일 분포 안에서만 해석한다.
 * @param mapX       경도. 응답에 포함되는지 확인되지 않아 없으면 null 로 둔다.
 * @param mapY       위도. 응답에 포함되는지 확인되지 않아 없으면 null 로 둔다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TatsCnctrRateItem(
        String baseYmd,
        String areaCd,
        String areaNm,
        String signguCd,
        String signguNm,
        String tAtsNm,
        String cnctrRate,
        String mapX,
        String mapY
) {
}
