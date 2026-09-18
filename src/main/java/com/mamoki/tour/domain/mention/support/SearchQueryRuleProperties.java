package com.mamoki.tour.domain.mention.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 온라인 언급량 검색어 규칙.
 *
 * @param includeSigungu 검색어에 시·군명을 넣을지. 동명 장소를 가르는 데 필요하다.
 * @param suffix         검색어 뒤에 붙일 말. 비우면 붙이지 않는다.
 * @param ambiguousRatio 이름이 변별력을 잃었다고 볼 기준. 이름+시·군 검색 결과가 시·군 단독
 *                       검색 결과의 이 비율을 넘으면 그 이름은 지역 전체와 구분되지 않는다.
 *                       실측에서 일반명사는 18~50%, 실제 관광지는 0~4.6% 였다.
 */
@ConfigurationProperties(prefix = "tour.api.naver.query")
public record SearchQueryRuleProperties(boolean includeSigungu, String suffix, double ambiguousRatio) {
}
