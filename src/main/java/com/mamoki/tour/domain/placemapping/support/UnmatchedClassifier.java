package com.mamoki.tour.domain.placemapping.support;

import java.util.Optional;

import com.mamoki.tour.domain.placemapping.enums.UnmatchedCategory;

/**
 * 카카오 판정으로 좁히지 못한 이름을 세 갈래로 나눈다.
 *
 * <p>왜 나누느냐 — 매칭률의 분모를 정리하려고다. 원천 파일에는 관광공사 카탈로그가 애초에
 * 담지 않는 장소가 섞여 있어, 그것까지 분모에 넣으면 "표기를 더 맞추면 얼마나 오를 수
 * 있는가" 를 가늠할 수 없다.
 *
 * <h2>순서</h2>
 * <ol>
 *   <li>이미 확정한 판정은 건드리지 않는다. 분류는 잇지 못한 이름의 이야기다</li>
 *   <li>표기만 걷어내 카탈로그에서 유일하게 되찾으면 확정으로 올린다 ({@code NAME_VARIANT})</li>
 *   <li>되찾았으나 좁히지 못했으면 저신뢰로 남긴다 ({@code NAME_VARIANT})</li>
 *   <li>카카오 카테고리와 이름 접미어가 둘 다 사전에 맞으면 {@code OUT_OF_CATALOG}</li>
 *   <li>그 밖에는 전부 {@code UNKNOWN}</li>
 * </ol>
 *
 * <p><b>표기 차이를 카탈로그 제외보다 앞에 두는 이유.</b> 카탈로그에 실제로 있는 장소를
 * 분모에서 빼면 매칭률이 두 번 좋아진다 — 분자에도 안 들어가고 분모에서도 빠진다. 되찾을
 * 수 있는 이름인지를 먼저 확인해야 그 일이 일어나지 않는다.
 */
public final class UnmatchedClassifier {

    private final OutOfCatalogRules rules;

    public UnmatchedClassifier(OutOfCatalogRules rules) {
        this.rules = rules;
    }

    /**
     * @param decision   카카오 판정 결과
     * @param sourceName 원천이 적은 이름
     * @param variant    표기만 걷어내 카탈로그를 되찾아 본 결과
     * @return 분류를 덧붙인 판정. 이미 확정한 판정은 그대로 돌려준다.
     */
    public PlaceMappingDecision classify(PlaceMappingDecision decision, String sourceName,
                                         NameVariantOutcome variant) {
        if (decision.isConfirmed()) {
            return decision;
        }

        if (variant.isUnique()) {
            CatalogCandidate target = variant.target();

            return decision.promotedToNameVariant(target.contentId(),
                    "표기(괄호·시·군 접두어)만 걷어내면 카탈로그 %s 와 유일하게 같아집니다."
                            .formatted(target.name()));
        }

        if (variant.isBlocked()) {
            return decision.ambiguousNameVariant(variant.blockedReason());
        }

        Optional<OutOfCatalogEvidence> evidence = rules.evaluate(sourceName,
                decision.kakaoCategoryGroupCode(), decision.kakaoCategoryName());

        return evidence
                .map(found -> decision.classifiedAs(UnmatchedCategory.OUT_OF_CATALOG,
                        found.categoryRule(), found.nameSuffixRule()))
                .orElseGet(() -> decision.classifiedAs(UnmatchedCategory.UNKNOWN, null, null));
    }
}
