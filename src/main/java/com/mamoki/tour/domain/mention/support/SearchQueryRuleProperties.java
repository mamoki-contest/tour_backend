package com.mamoki.tour.domain.mention.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 온라인 언급량 검색어 규칙.
 *
 * @param includeSigungu 검색어에 시·군명을 넣을지. 동명 장소를 가르는 데 필요하다.
 * @param suffix         검색어 뒤에 붙일 말. 비우면 붙이지 않는다.
 */
@ConfigurationProperties(prefix = "tour.api.naver.query")
public record SearchQueryRuleProperties(boolean includeSigungu, String suffix) {
}
