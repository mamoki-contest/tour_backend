package com.mamoki.tour.domain.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mamoki.tour.domain.search.entity.ThemeDefinition;
import com.mamoki.tour.domain.search.entity.ThemeSynonym;
import com.mamoki.tour.domain.search.enums.SupportedTheme;
import com.mamoki.tour.domain.search.repository.ThemeDefinitionRepository;
import com.mamoki.tour.domain.search.repository.ThemeSynonymRepository;
import com.mamoki.tour.domain.search.support.ThemeEntry;
import com.mamoki.tour.domain.search.support.ThemeResolver;

/**
 * {@code data.sql} 의 지원 테마 시드.
 *
 * <p>여기서 지키려는 것은 두 가지다. 시드가 실제로 적재되는지, 그리고 단위 테스트가
 * 쓰는 고정 목록({@code ThemeFixtures})과 <b>어긋나지 않는지</b>다. 어긋나면 단위
 * 테스트는 계속 통과하면서 실제 검색만 달라진다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SupportedThemeSeedTest {

    @Autowired
    private ThemeDefinitionRepository definitionRepository;

    @Autowired
    private ThemeSynonymRepository synonymRepository;

    @Autowired
    private ThemeResolver themeResolver;

    @Test
    @DisplayName("8개 지원 테마가 적재된다")
    void seedsAllSupportedThemes() {
        List<ThemeDefinition> definitions =
                definitionRepository.findAllByActiveTrueOrderBySortOrderAscCodeAsc();

        assertThat(definitions).hasSize(SupportedTheme.values().length);
        assertThat(definitions).extracting(ThemeDefinition::getCode)
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(SupportedTheme.values()).map(Enum::name).toList());
    }

    @Test
    @DisplayName("동의어의 정규화 형태가 실제 정규화 결과와 같다")
    void normalizedFormMatchesNormalizer() {
        // 두 값이 어긋나면 유니크 제약이 막아야 할 중복을 놓치게 된다.
        assertThat(synonymRepository.findAll()).allSatisfy(synonym ->
                assertThat(synonym.getNormalizedForm())
                        .isEqualTo(ThemeResolver.normalize(synonym.getSynonym())));
    }

    @Test
    @DisplayName("동의어는 저마다 하나의 테마만 가리킨다")
    void synonymsPointToOneThemeEach() {
        assertThat(synonymRepository.findAll())
                .extracting(ThemeSynonym::getNormalizedForm)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("동의어가 가리키는 테마 코드가 모두 정의에 있다")
    void synonymsReferenceExistingThemes() {
        assertThat(synonymRepository.findAll()).allSatisfy(synonym ->
                assertThat(definitionRepository.findByCode(synonym.getThemeCode())).isPresent());
    }

    @Test
    @DisplayName("기동 시 적재한 목록이 단위 테스트의 고정 목록과 같다")
    void loadedCatalogMatchesFixture() {
        assertThat(themeResolver.themes())
                .containsExactlyElementsOf(ThemeFixtures.seedThemes());
    }

    @Test
    @DisplayName("시드로 적재한 목록만으로 동의어와 오타가 해석된다")
    void resolvesFromSeededCatalog() {
        assertThat(themeResolver.match("해수욕장").map(ThemeEntry::code))
                .contains(SupportedTheme.BEACH);
        assertThat(themeResolver.match("바닷가").map(ThemeEntry::code))
                .contains(SupportedTheme.BEACH);
        assertThat(themeResolver.match("벗꽃").map(ThemeEntry::code))
                .contains(SupportedTheme.CHERRY_BLOSSOM);
        assertThat(themeResolver.match("강릉 맛집")).isEmpty();
    }
}
