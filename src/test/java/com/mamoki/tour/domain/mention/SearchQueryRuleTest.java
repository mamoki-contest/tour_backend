package com.mamoki.tour.domain.mention;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.mention.support.SearchQueryRule;
import com.mamoki.tour.domain.mention.support.SearchQueryRuleProperties;

class SearchQueryRuleTest {

    private SearchQueryRule rule(boolean includeSigungu, String suffix) {
        return new SearchQueryRule(new SearchQueryRuleProperties(includeSigungu, suffix));
    }

    @Test
    @DisplayName("PRD 규칙대로 관광지명 시·군명 여행 순으로 만든다")
    void buildsPrdRule() {
        assertThat(rule(true, "여행").build("경포해변", "강릉시"))
                .isEqualTo("경포해변 강릉시 여행");
    }

    @Test
    @DisplayName("접미어를 비우면 붙이지 않는다")
    void omitsBlankSuffix() {
        assertThat(rule(true, "").build("경포해변", "강릉시")).isEqualTo("경포해변 강릉시");
        assertThat(rule(true, null).build("경포해변", "강릉시")).isEqualTo("경포해변 강릉시");
    }

    @Test
    @DisplayName("시·군을 쓰지 않도록 설정할 수 있다")
    void canOmitSigungu() {
        assertThat(rule(false, "여행").build("경포해변", "강릉시")).isEqualTo("경포해변 여행");
    }

    @Test
    @DisplayName("시·군명이 없으면 생략하고 관광지명만 쓴다")
    void skipsMissingSigungu() {
        assertThat(rule(true, null).build("경포해변", null)).isEqualTo("경포해변");
        assertThat(rule(true, null).build("경포해변", "  ")).isEqualTo("경포해변");
    }

    @Test
    @DisplayName("표기 차이로 검색어가 갈라지지 않게 공백을 다듬는다")
    void normalizesWhitespace() {
        assertThat(rule(true, "여행").build("  비발디파크   오션월드 ", " 홍천군 "))
                .isEqualTo("비발디파크 오션월드 홍천군 여행");
    }

    @Test
    @DisplayName("관광지명이 없으면 검색어를 만들지 않는다")
    void returnsNullWithoutPlaceName() {
        assertThat(rule(true, "여행").build(null, "강릉시")).isNull();
        assertThat(rule(true, "여행").build("   ", "강릉시")).isNull();
    }

    @Test
    @DisplayName("버전은 규칙에서 직접 만들어져 규칙과 어긋날 수 없다")
    void derivesVersionFromRule() {
        assertThat(rule(true, "여행").version()).isEqualTo("name+sigungu+여행");
        assertThat(rule(true, null).version()).isEqualTo("name+sigungu");
        assertThat(rule(false, "여행").version()).isEqualTo("name+여행");
        assertThat(rule(false, null).version()).isEqualTo("name");
    }

    @Test
    @DisplayName("규칙이 다르면 버전도 다르다")
    void differentRulesHaveDifferentVersions() {
        assertThat(rule(true, "여행").version()).isNotEqualTo(rule(true, null).version());
    }
}
