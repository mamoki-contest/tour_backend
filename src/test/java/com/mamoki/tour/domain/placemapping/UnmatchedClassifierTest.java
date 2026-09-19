package com.mamoki.tour.domain.placemapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.placemapping.enums.PlaceMappingStatus;
import com.mamoki.tour.domain.placemapping.enums.PlaceMatchMethod;
import com.mamoki.tour.domain.placemapping.enums.UnmatchedCategory;
import com.mamoki.tour.domain.placemapping.support.CatalogCandidate;
import com.mamoki.tour.domain.placemapping.support.NameVariantOutcome;
import com.mamoki.tour.domain.placemapping.support.OutOfCatalogRules;
import com.mamoki.tour.domain.placemapping.support.PlaceMappingDecision;
import com.mamoki.tour.domain.placemapping.support.UnmatchedClassifier;
import com.mamoki.tour.infra.kakao.dto.KakaoPlace;

/**
 * 카카오 판정으로 좁히지 못한 이름을 세 갈래로 나눈다.
 *
 * <p>분모에서 빠지는 것은 {@code OUT_OF_CATALOG} 뿐이다. 그래서 확신이 서지 않는 것은
 * 전부 {@code UNKNOWN} 으로 남겨 분모에 둔다. 빼는 쪽으로 기울면 매칭률이 올라가는데
 * 그것이 좋아진 것인지 분모를 줄인 것인지 뒤에서 구별할 수 없다.
 */
class UnmatchedClassifierTest {

    private static final String GOLF_RULES = """
            [category-name]
            스포츠,레저 > 골프
            [name-suffix]
            CC
            """;

    private static final UnmatchedClassifier CLASSIFIER =
            new UnmatchedClassifier(OutOfCatalogRules.parse(GOLF_RULES));

    private static KakaoPlace golfCourse() {
        return new KakaoPlace("1", "남춘천컨트리클럽", null, "스포츠,레저 > 골프 > 골프장",
                "강원특별자치도 춘천시 남산면", null, "127.7", "37.8");
    }

    private static KakaoPlace attraction() {
        return new KakaoPlace("2", "청평사", "AT4", "여행 > 관광,명소 > 사찰",
                "강원특별자치도 춘천시 북산면", null, "127.8", "37.9");
    }

    private static CatalogCandidate candidate() {
        return new CatalogCandidate("100", "청평사(춘천)", new BigDecimal("37.9"), new BigDecimal("127.8"));
    }

    @Test
    @DisplayName("확정한 판정은 건드리지 않는다 — 분류는 잇지 못한 이름의 이야기다")
    void confirmedDecisionIsUntouched() {
        PlaceMappingDecision confirmed = PlaceMappingDecision.confirmed(attraction(), "100",
                PlaceMatchMethod.EXACT, BigDecimal.ONE, 12.0, "이름 일치");

        PlaceMappingDecision classified =
                CLASSIFIER.classify(confirmed, "청평사", NameVariantOutcome.NONE);

        assertThat(classified).isSameAs(confirmed);
        assertThat(classified.category()).isNull();
    }

    @Test
    @DisplayName("표기 차이로 유일하게 되찾으면 확정으로 올린다")
    void uniqueNameVariantIsPromoted() {
        PlaceMappingDecision unmatched =
                PlaceMappingDecision.unmatched(attraction(), "이을 후보가 없습니다.");

        PlaceMappingDecision classified = CLASSIFIER.classify(unmatched, "청평사",
                NameVariantOutcome.unique(candidate()));

        assertThat(classified.status()).isEqualTo(PlaceMappingStatus.CONFIRMED);
        assertThat(classified.contentId()).isEqualTo("100");
        assertThat(classified.method()).isEqualTo(PlaceMatchMethod.NAME_VARIANT);
        assertThat(classified.category()).isEqualTo(UnmatchedCategory.NAME_VARIANT);
    }

    @Test
    @DisplayName("되찾았으나 하나로 좁히지 못하면 저신뢰로 남기고 값을 만들지 않는다")
    void blockedNameVariantStaysLowConfidence() {
        PlaceMappingDecision unmatched =
                PlaceMappingDecision.unmatched(attraction(), "이을 후보가 없습니다.");

        PlaceMappingDecision classified = CLASSIFIER.classify(unmatched, "청평사",
                NameVariantOutcome.blocked("같은 카탈로그를 가리키는 이름이 둘입니다."));

        assertThat(classified.status()).isEqualTo(PlaceMappingStatus.LOW_CONFIDENCE);
        assertThat(classified.contentId()).isNull();
        assertThat(classified.category()).isEqualTo(UnmatchedCategory.NAME_VARIANT);
    }

    @Test
    @DisplayName("표기 차이가 카탈로그 대상 여부보다 앞선다 — 카탈로그에 있는 것을 빼지 않는다")
    void nameVariantWinsOverOutOfCatalog() {
        PlaceMappingDecision unmatched =
                PlaceMappingDecision.unmatched(golfCourse(), "이을 후보가 없습니다.");

        PlaceMappingDecision classified = CLASSIFIER.classify(unmatched, "남춘천CC",
                NameVariantOutcome.unique(candidate()));

        assertThat(classified.category()).isEqualTo(UnmatchedCategory.NAME_VARIANT);
        assertThat(classified.status()).isEqualTo(PlaceMappingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("카테고리와 접미어가 둘 다 맞으면 카탈로그 대상이 아니라고 적고 근거를 남긴다")
    void outOfCatalogKeepsItsEvidence() {
        PlaceMappingDecision unmatched =
                PlaceMappingDecision.unmatched(golfCourse(), "이을 후보가 없습니다.");

        PlaceMappingDecision classified =
                CLASSIFIER.classify(unmatched, "남춘천CC", NameVariantOutcome.NONE);

        assertThat(classified.category()).isEqualTo(UnmatchedCategory.OUT_OF_CATALOG);
        assertThat(classified.categoryRule()).isEqualTo("스포츠,레저 > 골프");
        assertThat(classified.nameSuffixRule()).isEqualTo("CC");
        assertThat(classified.kakaoCategoryName()).isEqualTo("스포츠,레저 > 골프 > 골프장");
        assertThat(classified.status()).isEqualTo(PlaceMappingStatus.UNMATCHED);
    }

    @Test
    @DisplayName("한쪽만 맞으면 모르는 것으로 남겨 분모에 둔다")
    void oneSignalOnlyStaysUnknown() {
        PlaceMappingDecision unmatched =
                PlaceMappingDecision.unmatched(attraction(), "이을 후보가 없습니다.");

        PlaceMappingDecision classified =
                CLASSIFIER.classify(unmatched, "남춘천CC", NameVariantOutcome.NONE);

        assertThat(classified.category()).isEqualTo(UnmatchedCategory.UNKNOWN);
        assertThat(classified.categoryRule()).isNull();
        assertThat(classified.nameSuffixRule()).isNull();
    }

    @Test
    @DisplayName("카카오를 부르지도 못한 이름은 모르는 것으로 남는다")
    void withoutKakaoEvidenceStaysUnknown() {
        PlaceMappingDecision unmatched =
                PlaceMappingDecision.unmatched("카카오 검색 결과가 없습니다.");

        PlaceMappingDecision classified =
                CLASSIFIER.classify(unmatched, "남춘천CC", NameVariantOutcome.NONE);

        assertThat(classified.category()).isEqualTo(UnmatchedCategory.UNKNOWN);
    }

    @Test
    @DisplayName("저신뢰도 분류한다 — 좁히지 못한 것은 모두 분모 이야기다")
    void lowConfidenceIsClassifiedToo() {
        PlaceMappingDecision lowConfidence = PlaceMappingDecision.lowConfidence(golfCourse(),
                "100", new BigDecimal("0.500"), 150.0, "경계 안 후보가 둘입니다.");

        PlaceMappingDecision classified =
                CLASSIFIER.classify(lowConfidence, "남춘천CC", NameVariantOutcome.NONE);

        assertThat(classified.status()).isEqualTo(PlaceMappingStatus.LOW_CONFIDENCE);
        assertThat(classified.category()).isEqualTo(UnmatchedCategory.OUT_OF_CATALOG);
    }
}
