package com.mamoki.tour.domain.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Sort;

import com.mamoki.tour.domain.search.entity.ThemeDefinition;
import com.mamoki.tour.domain.search.entity.ThemeSynonym;
import com.mamoki.tour.domain.search.enums.SupportedTheme;
import com.mamoki.tour.domain.search.repository.ThemeDefinitionRepository;
import com.mamoki.tour.domain.search.repository.ThemeSynonymRepository;
import com.mamoki.tour.domain.search.support.ThemeEntry;
import com.mamoki.tour.domain.search.support.ThemeResolver;

/**
 * 시드를 읽어 메모리에 올리는 단계.
 *
 * <p>여기서 보는 것은 시드가 잘못됐을 때의 행동이다. 모두 "검색이 그냥 안 걸린다" 로만
 * 드러나는 종류라, 넘겨짚지 않고 닫는 쪽으로 틀리는지 확인한다.
 */
class ThemeCatalogLoadTest {

    private final ThemeDefinitionRepository definitionRepository =
            Mockito.mock(ThemeDefinitionRepository.class);
    private final ThemeSynonymRepository synonymRepository =
            Mockito.mock(ThemeSynonymRepository.class);

    @Test
    @DisplayName("정의와 동의어를 합쳐 목록을 만든다")
    void loadsDefinitionsWithSynonyms() {
        givenSeed(List.of(definition("BEACH", "해수욕장", "해수욕장,해변", "해수욕장,해변")),
                List.of(synonym("BEACH", "해수욕장"), synonym("BEACH", "바닷가")));

        List<ThemeEntry> themes = load();

        assertThat(themes).hasSize(1);
        assertThat(themes.get(0)).isEqualTo(new ThemeEntry(SupportedTheme.BEACH, "해수욕장",
                List.of("해수욕장", "바닷가"), List.of("해수욕장", "해변"), List.of("해수욕장", "해변")));
    }

    @Test
    @DisplayName("쉼표 구분값의 공백과 빈 조각은 버린다")
    void trimsDelimitedValues() {
        givenSeed(List.of(definition("VALLEY", "계곡", " 계곡 , , 계곡물 ", "계곡,,계곡")),
                List.of(synonym("VALLEY", "계곡")));

        assertThat(load().get(0).keywords()).containsExactly("계곡", "계곡물");
        assertThat(load().get(0).matchTokens()).containsExactly("계곡");
    }

    @Test
    @DisplayName("모르는 테마 코드는 건너뛴다")
    void skipsUnknownCode() {
        // 시드가 코드보다 앞서갔거나 오타가 난 경우다. 임의의 테마로 넘겨짚지 않는다.
        givenSeed(List.of(definition("SKI_RESORT", "스키장", "스키장", "스키장")),
                List.of(synonym("SKI_RESORT", "스키장")));

        assertThat(load()).isEmpty();
    }

    @Test
    @DisplayName("공급자 검색어가 없는 테마는 목록에서 뺀다")
    void skipsThemeWithoutKeywords() {
        // 남겨 두면 "부를 것이 없었다" 가 "공급자가 답하지 않았다" 로 보고된다.
        givenSeed(List.of(definition("BEACH", "해수욕장", "  ", "해수욕장")),
                List.of(synonym("BEACH", "해수욕장")));

        assertThat(load()).isEmpty();
    }

    @Test
    @DisplayName("자격 토큰이 없는 테마는 남기되 아무 장소도 담지 못한다")
    void keepsThemeWithoutMatchTokens() {
        givenSeed(List.of(definition("BEACH", "해수욕장", "해수욕장", "")),
                List.of(synonym("BEACH", "해수욕장")));

        assertThat(load()).hasSize(1);
        assertThat(load().get(0).matchTokens()).isEmpty();
    }

    @Test
    @DisplayName("같은 동의어가 두 테마를 가리키면 앞선 테마가 가져간다")
    void firstThemeClaimsSharedSynonym() {
        // DB 유니크 제약이 먼저 막지만, 우회해 들어온 데이터에서도 결과가 적재 순서에
        // 따라 달라지면 안 된다.
        givenSeed(
                List.of(definition("BEACH", "해수욕장", "해수욕장", "해수욕장"),
                        definition("VALLEY", "계곡", "계곡", "계곡")),
                List.of(synonym("BEACH", "물놀이"), synonym("VALLEY", "물놀이"),
                        synonym("VALLEY", "계곡")));

        List<ThemeEntry> themes = load();

        assertThat(themes.get(0).aliases()).containsExactly("물놀이");
        assertThat(themes.get(1).aliases()).containsExactly("계곡");
    }

    @Test
    @DisplayName("표가 비어 있으면 빈 목록을 올린다")
    void loadsNothingFromEmptySeed() {
        givenSeed(List.of(), List.of());

        assertThat(load()).isEmpty();
    }

    private List<ThemeEntry> load() {
        ThemeResolver resolver = new ThemeResolver(definitionRepository, synonymRepository);
        resolver.load();

        return resolver.themes();
    }

    private void givenSeed(List<ThemeDefinition> definitions, List<ThemeSynonym> synonyms) {
        given(definitionRepository.findAllByActiveTrueOrderBySortOrderAscCodeAsc())
                .willReturn(definitions);
        given(synonymRepository.findAll(Mockito.any(Sort.class))).willReturn(synonyms);
    }

    private static ThemeDefinition definition(String code, String displayName,
                                              String keywords, String matchTokens) {

        return ThemeDefinition.builder()
                .code(code)
                .displayName(displayName)
                .searchKeywords(keywords)
                .matchTokens(matchTokens)
                .active(true)
                .sortOrder(1)
                .build();
    }

    private static ThemeSynonym synonym(String themeCode, String synonym) {
        return ThemeSynonym.builder()
                .themeCode(themeCode)
                .synonym(synonym)
                .normalizedForm(ThemeResolver.normalize(synonym))
                .build();
    }
}
