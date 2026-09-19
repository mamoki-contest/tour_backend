package com.mamoki.tour.domain.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.mamoki.tour.domain.search.enums.SupportedTheme;
import com.mamoki.tour.domain.search.support.ThemeEntry;
import com.mamoki.tour.domain.search.support.ThemeResolver;

/**
 * 검색어를 지원 테마로 잇는 규칙.
 *
 * <p>지원 테마 표시는 추천 자격을 적용했다는 보증이라, 넓게 잡히는 쪽보다
 * 엉뚱한 말이 테마로 둔갑하지 않는 쪽을 확인한다.
 *
 * <p>테마 목록은 시드에서 온다. 여기서는 {@code data.sql} 과 같은 내용의 고정 목록을
 * 올려 규칙만 본다. 시드 자체가 이 목록과 같은지는 {@code SupportedThemeSeedTest} 가 본다.
 */
class ThemeResolverTest {

    private final ThemeResolver resolver = ThemeResolver.withThemes(ThemeFixtures.seedThemes());

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
        assertThat(resolve(query)).contains(expected);
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
        assertThat(resolve(query)).contains(expected);
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
        assertThat(resolve(query)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"강릉 맛집", "스키장", "카페", "짬뽕", "속초 숙소"})
    @DisplayName("지원하지 않는 검색어는 테마로 잇지 않는다")
    void doesNotResolveUnsupportedQueries(String query) {
        assertThat(resolve(query)).isEmpty();
    }

    @Test
    @DisplayName("공백과 대소문자를 지운 뒤 맞춘다")
    void normalizesWhitespaceAndCase() {
        assertThat(resolve(" 해수욕장 ")).contains(SupportedTheme.BEACH);
        assertThat(resolve("BEACH")).contains(SupportedTheme.BEACH);
        assertThat(resolve("꽃 축제")).contains(SupportedTheme.FLOWER_FESTIVAL);
    }

    @Test
    @DisplayName("빈 검색어는 테마로 잇지 않는다")
    void handlesBlankQuery() {
        assertThat(resolve(null)).isEmpty();
        assertThat(resolve("   ")).isEmpty();
        assertThat(ThemeResolver.normalize("   ")).isNull();
    }

    @Test
    @DisplayName("두 글자 이상 다르면 다른 말로 본다")
    void rejectsDistantQueries() {
        assertThat(resolve("해장국")).isEmpty();
    }

    @Test
    @DisplayName("가까운 테마를 제안한다")
    void suggestsNearbyThemes() {
        List<SupportedTheme> suggested = suggest("계고");

        assertThat(suggested).contains(SupportedTheme.VALLEY);
        assertThat(suggested).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("먼 검색어에는 억지로 테마를 권하지 않는다")
    void suggestsNothingForDistantQuery() {
        assertThat(suggest("강릉시내버스시간표")).isEmpty();
    }

    @Test
    @DisplayName("이어진 테마는 표시명과 공급자 검색어를 함께 준다")
    void carriesDisplayNameAndKeywords() {
        ThemeEntry beach = resolver.match("바닷가").orElseThrow();

        assertThat(beach.displayName()).isEqualTo("해수욕장");
        assertThat(beach.keywords()).containsExactly("해수욕장", "해변");
        assertThat(beach.matchTokens()).containsExactly("해수욕장", "해변");
    }

    @Test
    @DisplayName("시드가 비어 있으면 어떤 검색어도 테마로 잇지 않고 제안도 하지 않는다")
    void resolvesNothingWithoutSeed() {
        // 오류가 아니라 지원 테마가 없는 상태다. 코드에 남은 옛 목록으로 대신하지 않는다.
        ThemeResolver empty = ThemeResolver.withThemes(List.of());

        assertThat(empty.match("해수욕장")).isEmpty();
        assertThat(empty.match("바닷가")).isEmpty();
        assertThat(empty.suggest("계고")).isEmpty();
    }

    @Test
    @DisplayName("동의어가 없는 테마는 어떤 검색어로도 이어지지 않는다")
    void neverMatchesThemeWithoutSynonyms() {
        ThemeResolver resolver = ThemeResolver.withThemes(List.of(
                new ThemeEntry(SupportedTheme.BEACH, "해수욕장", List.of(),
                        List.of("해수욕장"), List.of("해수욕장"))));

        assertThat(resolver.match("해수욕장")).isEmpty();
        assertThat(resolver.suggest("해수욕장")).isEmpty();
    }

    @Test
    @DisplayName("같은 거리의 제안은 시드가 정한 순서로 앞선다")
    void ordersSuggestionsBySeedOrder() {
        // 앞자리가 시드 순서를 따르지 않으면 같은 검색어의 제안이 기동마다 달라질 수 있다.
        ThemeResolver resolver = ThemeResolver.withThemes(List.of(
                new ThemeEntry(SupportedTheme.VALLEY, "계곡", List.of("계곡"),
                        List.of("계곡"), List.of("계곡")),
                new ThemeEntry(SupportedTheme.SNOW_FLOWER, "눈꽃", List.of("눈꽃"),
                        List.of("눈꽃"), List.of("눈꽃"))));

        // `가나` 는 두 테마 모두와 두 글자씩 다르다.
        assertThat(resolver.suggest("가나").stream().map(ThemeEntry::code))
                .containsExactly(SupportedTheme.VALLEY, SupportedTheme.SNOW_FLOWER);
    }

    private Optional<SupportedTheme> resolve(String query) {
        return resolver.match(query).map(ThemeEntry::code);
    }

    private List<SupportedTheme> suggest(String query) {
        return resolver.suggest(query).stream().map(ThemeEntry::code).toList();
    }
}
