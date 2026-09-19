package com.mamoki.tour.domain.placemapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.placemapping.support.OutOfCatalogEvidence;
import com.mamoki.tour.domain.placemapping.support.OutOfCatalogRules;

/**
 * 카탈로그 대상이 아닌 장소를 가려내는 사전.
 *
 * <p>여기서 틀리면 조용하다. 아닌 것을 {@code OUT_OF_CATALOG} 로 부르면 분모에서 빠지고
 * 매칭률만 좋아진다. 에러는 나지 않는다. 그래서 규칙은 <b>둘 다 맞을 때만</b> 서고,
 * 카탈로그가 실제로 담고 있는 종류는 규칙이 적혀 있어도 꺼진다.
 */
class OutOfCatalogRulesTest {

    private static final String SAMPLE = """
            # 주석과 빈 줄은 건너뛴다

            [category-group]
            HP8

            [category-name]
            스포츠,레저 > 골프
            의료,건강 > 병원

            [name-suffix]
            골프장
            CC
            병원
            """;

    private static OutOfCatalogRules rules() {
        return OutOfCatalogRules.parse(SAMPLE);
    }

    @Test
    @DisplayName("카테고리와 이름 접미어가 둘 다 맞으면 카탈로그 대상이 아니다")
    void bothSignalsMakeItOutOfCatalog() {
        OutOfCatalogEvidence evidence = rules()
                .evaluate("남춘천CC", null, "스포츠,레저 > 골프 > 골프장")
                .orElseThrow();

        assertThat(evidence.categoryRule()).isEqualTo("스포츠,레저 > 골프");
        assertThat(evidence.nameSuffixRule()).isEqualTo("CC");
    }

    @Test
    @DisplayName("category_group_code 로도 카테고리 쪽이 선다")
    void categoryGroupCodeAlsoCounts() {
        assertThat(rules().evaluate("속초김안과병원", "HP8", "의료,건강 > 병원 > 안과"))
                .isPresent();
    }

    @Test
    @DisplayName("카테고리만 맞으면 판정하지 않는다")
    void categoryAloneIsNotEnough() {
        assertThat(rules().evaluate("대관령삼양목장", null, "스포츠,레저 > 골프 > 골프장"))
                .isEmpty();
    }

    @Test
    @DisplayName("이름 접미어만 맞으면 판정하지 않는다")
    void nameSuffixAloneIsNotEnough() {
        assertThat(rules().evaluate("남춘천CC", "AT4", "여행 > 관광,명소"))
                .isEmpty();
    }

    @Test
    @DisplayName("카카오 결과가 없어 카테고리를 모르면 판정하지 않는다")
    void unknownCategoryIsNotEnough() {
        assertThat(rules().evaluate("남춘천CC", null, null)).isEmpty();
    }

    @Test
    @DisplayName("이름 가운데에 든 말은 접미어가 아니다")
    void suffixMustBeAtTheEnd() {
        assertThat(rules().evaluate("세레니티CC강촌", null, "스포츠,레저 > 골프 > 골프장"))
                .isEmpty();
    }

    @Test
    @DisplayName("카탈로그가 담고 있는 종류는 규칙이 적혀 있어도 꺼진다")
    void suffixPresentInCatalogIsSuppressed() {
        OutOfCatalogRules withEvidence = rules()
                .withCatalogEvidence(List.of("오크밸리CC", "보광미니골프장", "경포해수욕장"));

        assertThat(withEvidence.evaluate("남춘천CC", null, "스포츠,레저 > 골프 > 골프장"))
                .isEmpty();
        assertThat(withEvidence.suppressedSuffixes())
                .containsExactlyInAnyOrder("골프장", "CC");
    }

    @Test
    @DisplayName("카탈로그에 없는 종류는 그대로 선다")
    void suffixAbsentFromCatalogStaysActive() {
        OutOfCatalogRules withEvidence = rules()
                .withCatalogEvidence(List.of("오크밸리CC", "경포해수욕장"));

        assertThat(withEvidence.evaluate("속초김안과병원", "HP8", "의료,건강 > 병원 > 안과"))
                .isPresent();
    }

    @Test
    @DisplayName("저장소에 둔 사전 파일을 읽는다")
    void loadsBundledDictionary() {
        OutOfCatalogRules loaded = OutOfCatalogRules.load();

        assertThat(loaded.categoryRuleCount()).isPositive();
        assertThat(loaded.nameSuffixRuleCount()).isPositive();
        assertThat(loaded.evaluate("남춘천CC", null, "스포츠,레저 > 골프 > 골프장")).isPresent();
    }
}
