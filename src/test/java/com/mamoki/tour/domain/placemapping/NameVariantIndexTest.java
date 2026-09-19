package com.mamoki.tour.domain.placemapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.placemapping.support.CatalogCandidate;
import com.mamoki.tour.domain.placemapping.support.NameVariantIndex;

/**
 * 표기 차이만으로 갈린 이름을 카탈로그에서 되찾는 색인.
 *
 * <p>되찾는 것보다 <b>둘 이상을 하나로 좁히지 않는 것</b>이 중요하다. 괄호를 걷어내면
 * 서로 다른 장소가 같은 이름이 되는 경우가 있고({@code 설악산(오색지구)} 와
 * {@code 설악산(백담지구)}), 그때 하나를 골라 확정하면 틀린 장소에 값이 붙는다.
 */
class NameVariantIndexTest {

    private static CatalogCandidate catalog(String contentId, String name) {
        return new CatalogCandidate(contentId, name, null, null);
    }

    @Test
    @DisplayName("괄호 별칭만 다른 카탈로그 이름을 되찾는다")
    void findsCandidateWithParenthesizedAlias() {
        NameVariantIndex index = NameVariantIndex.of("강릉시", List.of(
                catalog("1", "사근진해변(사근진해수욕장)"),
                catalog("2", "경포해수욕장")));

        assertThat(index.candidatesFor("사근진해변"))
                .extracting(CatalogCandidate::contentId)
                .containsExactly("1");
    }

    @Test
    @DisplayName("시·군 접두어만 다른 카탈로그 이름을 되찾는다")
    void findsCandidateWithRegionPrefix() {
        NameVariantIndex index = NameVariantIndex.of("삼척시", List.of(
                catalog("1", "삼척 죽서루")));

        assertThat(index.candidatesFor("죽서루"))
                .extracting(CatalogCandidate::contentId)
                .containsExactly("1");
    }

    @Test
    @DisplayName("원천 이름 쪽 시·군 접두어도 걷어낸다")
    void stripsRegionPrefixFromSourceName() {
        NameVariantIndex index = NameVariantIndex.of("홍천군", List.of(
                catalog("1", "무궁화수목원")));

        assertThat(index.candidatesFor("홍천무궁화수목원"))
                .extracting(CatalogCandidate::contentId)
                .containsExactly("1");
    }

    @Test
    @DisplayName("걷어낸 뒤 같아지는 카탈로그가 둘이면 둘 다 돌려준다 — 고르지 않는다")
    void returnsAllCandidatesWhenAmbiguous() {
        NameVariantIndex index = NameVariantIndex.of("속초시", List.of(
                catalog("1", "설악산(오색지구)"),
                catalog("2", "설악산(백담지구)")));

        assertThat(index.candidatesFor("설악산")).hasSize(2);
    }

    @Test
    @DisplayName("표기를 걷어내도 같아지지 않으면 빈 목록이다")
    void returnsEmptyWhenNothingMatches() {
        NameVariantIndex index = NameVariantIndex.of("춘천시", List.of(
                catalog("1", "남춘천컨트리클럽")));

        assertThat(index.candidatesFor("남춘천CC")).isEmpty();
    }

    @Test
    @DisplayName("정규화할 수 없는 이름은 빈 목록이다")
    void returnsEmptyForUnnormalizableName() {
        NameVariantIndex index = NameVariantIndex.of("춘천시", List.of(catalog("1", "청평사")));

        assertThat(index.candidatesFor("(폐역)")).isEmpty();
        assertThat(index.candidatesFor(null)).isEmpty();
    }
}
