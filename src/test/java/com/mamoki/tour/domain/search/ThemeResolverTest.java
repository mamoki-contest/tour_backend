package com.mamoki.tour.domain.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.mamoki.tour.domain.search.enums.SupportedTheme;
import com.mamoki.tour.domain.search.support.ThemeResolver;

/**
 * 검색어를 지원 테마로 잇는 규칙.
 *
 * <p>지원 테마 표시는 추천 자격을 적용했다는 보증이라, 넓게 잡히는 쪽보다
 * 엉뚱한 말이 테마로 둔갑하지 않는 쪽을 확인한다.
 */
class ThemeResolverTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "벚꽃, CHERRY_BLOSSOM",
            "꽃축제, FLOWER_FESTIVAL",
            "해수욕장, BEACH",
            "계곡, VALLEY",
            "단풍, AUTUMN_FOLIAGE",
            "억새, SILVER_GRASS",
            "눈꽃, SNOW_FLOWER",
            "해돋이, SUNRISE"
    })
    @DisplayName("8개 지원 테마 이름이 그대로 이어진다")
    void resolvesAllSupportedThemes(String query, SupportedTheme expected) {
        assertThat(ThemeResolver.resolve(query)).contains(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "바닷가, BEACH",
            "비치, BEACH",
            "해변, BEACH",
            "일출, SUNRISE",
            "설경, SNOW_FLOWER",
            "억새밭, SILVER_GRASS",
            "단풍놀이, AUTUMN_FOLIAGE",
            "꽃놀이, FLOWER_FESTIVAL"
    })
    @DisplayName("동의어가 지원 테마로 이어진다")
    void resolvesSynonyms(String query, SupportedTheme expected) {
        assertThat(ThemeResolver.resolve(query)).contains(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "벗꽃, CHERRY_BLOSSOM",
            "벛꽃, CHERRY_BLOSSOM",
            "계골, VALLEY",
            "단푸, AUTUMN_FOLIAGE"
    })
    @DisplayName("한 글자 차이의 명백한 오타는 지원 테마로 이어진다")
    void resolvesObviousTypos(String query, SupportedTheme expected) {
        assertThat(ThemeResolver.resolve(query)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"강릉 맛집", "스키장", "카페", "짬뽕", "속초 숙소"})
    @DisplayName("지원하지 않는 검색어는 테마로 잇지 않는다")
    void doesNotResolveUnsupportedQueries(String query) {
        assertThat(ThemeResolver.resolve(query)).isEmpty();
    }

    @Test
    @DisplayName("공백과 대소문자를 지운 뒤 맞춘다")
    void normalizesWhitespaceAndCase() {
        assertThat(ThemeResolver.resolve(" 해수욕장 ")).contains(SupportedTheme.BEACH);
        assertThat(ThemeResolver.resolve("BEACH")).contains(SupportedTheme.BEACH);
        assertThat(ThemeResolver.resolve("꽃 축제")).contains(SupportedTheme.FLOWER_FESTIVAL);
    }

    @Test
    @DisplayName("빈 검색어는 테마로 잇지 않는다")
    void handlesBlankQuery() {
        assertThat(ThemeResolver.resolve(null)).isEmpty();
        assertThat(ThemeResolver.resolve("   ")).isEmpty();
        assertThat(ThemeResolver.normalize("   ")).isNull();
    }

    @Test
    @DisplayName("두 글자 이상 다르면 다른 말로 본다")
    void rejectsDistantQueries() {
        assertThat(ThemeResolver.resolve("해장국")).isEmpty();
    }

    @Test
    @DisplayName("가까운 테마를 제안한다")
    void suggestsNearbyThemes() {
        List<SupportedTheme> suggested = ThemeResolver.suggest("계고");

        assertThat(suggested).contains(SupportedTheme.VALLEY);
        assertThat(suggested).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("먼 검색어에는 억지로 테마를 권하지 않는다")
    void suggestsNothingForDistantQuery() {
        assertThat(ThemeResolver.suggest("강릉시내버스시간표")).isEmpty();
    }
}
