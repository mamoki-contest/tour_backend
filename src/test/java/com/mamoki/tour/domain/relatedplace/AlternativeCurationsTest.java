package com.mamoki.tour.domain.relatedplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.relatedplace.dto.RelatedPlace;
import com.mamoki.tour.domain.relatedplace.entity.AlternativeCuration;
import com.mamoki.tour.domain.relatedplace.enums.CurationAction;
import com.mamoki.tour.domain.relatedplace.enums.RelatedPlaceKind;
import com.mamoki.tour.domain.relatedplace.support.AlternativeCurations;

/**
 * 자격을 충족한 대체지 후보에 운영 판단을 덧입히는 규칙.
 *
 * <p>여기서 보는 것은 세 가지다. 뺄 것을 빼는지, 대표를 앞세우면서 나머지 순서를
 * 흐트러뜨리지 않는지, 그리고 <b>지금 적용할 수 없는 판단</b>(테마 맥락이 필요한 행)을
 * 넘겨짚어 적용하지 않는지다.
 */
class AlternativeCurationsTest {

    private static final String BASE = "125787";
    private static final String GANGNEUNG = "51150";
    private static final String SOKCHO = "51210";

    @Test
    @DisplayName("EXCLUDE 는 자격을 충족한 후보를 뺀다")
    void excludesCuratedPlace() {
        List<RelatedPlace> curated = AlternativeCurations.apply(
                List.of(place("정동진", GANGNEUNG, 1), place("주문진항", GANGNEUNG, 2)),
                List.of(curation(CurationAction.EXCLUDE, "정동진", GANGNEUNG)));

        assertThat(curated).extracting(RelatedPlace::name).containsExactly("주문진항");
    }

    @Test
    @DisplayName("REPRESENTATIVE 는 후보를 맨 앞으로 옮긴다")
    void movesRepresentativeToFront() {
        List<RelatedPlace> curated = AlternativeCurations.apply(
                List.of(place("정동진", GANGNEUNG, 1), place("주문진항", GANGNEUNG, 2),
                        place("속초해수욕장", SOKCHO, 3)),
                List.of(curation(CurationAction.REPRESENTATIVE, "속초해수욕장", SOKCHO)));

        assertThat(curated).extracting(RelatedPlace::name)
                .containsExactly("속초해수욕장", "정동진", "주문진항");
    }

    @Test
    @DisplayName("대표를 앞세워도 나머지는 공급자 연관 순위 그대로 남는다")
    void keepsProviderOrderAmongTheRest() {
        List<RelatedPlace> curated = AlternativeCurations.apply(
                List.of(place("정동진", GANGNEUNG, 1), place("주문진항", GANGNEUNG, 2),
                        place("속초해수욕장", SOKCHO, 3), place("낙산사", SOKCHO, 4)),
                List.of(curation(CurationAction.REPRESENTATIVE, "낙산사", SOKCHO),
                        curation(CurationAction.REPRESENTATIVE, "주문진항", GANGNEUNG)));

        // 대표끼리도 순위 순서를 지킨다. 표에 적힌 순서로 뒤집지 않는다.
        assertThat(curated).extracting(RelatedPlace::name)
                .containsExactly("주문진항", "낙산사", "정동진", "속초해수욕장");
    }

    @Test
    @DisplayName("같은 대상에 두 판단이 걸리면 EXCLUDE 가 이긴다")
    void excludeBeatsRepresentative() {
        List<RelatedPlace> curated = AlternativeCurations.apply(
                List.of(place("정동진", GANGNEUNG, 1), place("주문진항", GANGNEUNG, 2)),
                List.of(curation(CurationAction.REPRESENTATIVE, "정동진", GANGNEUNG),
                        curation(CurationAction.EXCLUDE, "정동진", GANGNEUNG)));

        assertThat(curated).extracting(RelatedPlace::name).containsExactly("주문진항");
    }

    @Test
    @DisplayName("이름이 같아도 시·군이 다르면 적용되지 않는다")
    void doesNotApplyAcrossRegions() {
        List<RelatedPlace> curated = AlternativeCurations.apply(
                List.of(place("해수욕장", GANGNEUNG, 1)),
                List.of(curation(CurationAction.EXCLUDE, "해수욕장", SOKCHO)));

        assertThat(curated).extracting(RelatedPlace::name).containsExactly("해수욕장");
    }

    @Test
    @DisplayName("표기가 달라도 정규화하면 같은 대상으로 본다")
    void matchesNormalizedName() {
        List<RelatedPlace> curated = AlternativeCurations.apply(
                List.of(place("경포 해변", GANGNEUNG, 1)),
                List.of(curation(CurationAction.EXCLUDE, "경포해변", GANGNEUNG)));

        assertThat(curated).isEmpty();
    }

    @Test
    @DisplayName("테마 코드가 적힌 판단은 테마 맥락이 없는 상세에서 적용되지 않는다")
    void ignoresThemeScopedCuration() {
        // 맥락 없이 적용하면 그 테마 밖에서까지 판단이 새어 나간다.
        AlternativeCuration themeScoped = AlternativeCuration.builder()
                .baseContentId(BASE)
                .targetNormalizedName("정동진")
                .targetLawdCode(GANGNEUNG)
                .action(CurationAction.EXCLUDE)
                .themeCode("BEACH")
                .reason("해수욕장 테마에서만 부적절")
                .build();

        List<RelatedPlace> curated = AlternativeCurations.apply(
                List.of(place("정동진", GANGNEUNG, 1)), List.of(themeScoped));

        assertThat(curated).extracting(RelatedPlace::name).containsExactly("정동진");
    }

    @Test
    @DisplayName("표가 비어 있으면 자격 판정 결과가 그대로 나간다")
    void keepsEligibleListWhenEmpty() {
        List<RelatedPlace> eligible = List.of(place("정동진", GANGNEUNG, 1));

        assertThat(AlternativeCurations.apply(eligible, List.of())).isEqualTo(eligible);
    }

    @Test
    @DisplayName("후보에 없는 대상을 가리키는 판단은 아무 일도 하지 않는다")
    void ignoresCurationForAbsentPlace() {
        List<RelatedPlace> eligible = List.of(place("정동진", GANGNEUNG, 1));

        List<RelatedPlace> curated = AlternativeCurations.apply(
                eligible, List.of(curation(CurationAction.REPRESENTATIVE, "없는곳", GANGNEUNG)));

        assertThat(curated).extracting(RelatedPlace::name).containsExactly("정동진");
    }

    private static RelatedPlace place(String name, String lawdCode, int rank) {
        return new RelatedPlace(name, RelatedPlaceKind.ATTRACTION, "관광지", null, null,
                lawdCode, "시군", rank, true, null);
    }

    private static AlternativeCuration curation(CurationAction action, String name, String lawdCode) {
        return AlternativeCuration.builder()
                .baseContentId(BASE)
                .targetNormalizedName(name)
                .targetLawdCode(lawdCode)
                .action(action)
                .reason("테스트 fixture")
                .build();
    }
}
