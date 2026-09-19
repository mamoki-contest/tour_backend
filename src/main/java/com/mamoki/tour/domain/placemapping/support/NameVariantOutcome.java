package com.mamoki.tour.domain.placemapping.support;

/**
 * 표기만 걷어내 카탈로그를 되찾아 본 결과.
 *
 * <p>세 가지다 — 되찾지 못했거나({@link #NONE}), 한 곳으로 좁혔거나({@link #unique}),
 * 되찾긴 했지만 확정할 수 없거나({@link #blocked}). 셋째를 둘째와 뭉뚱그리지 않는 것이
 * 중요하다. 되찾았는데 좁히지 못한 이름은 "표기 차이" 로 분류되어야 하고, 그 사실이
 * 없으면 규칙을 더 손볼 자리가 어디인지 알 수 없다.
 *
 * @param target        확정해도 되는 카탈로그 후보. 좁히지 못했으면 null.
 * @param blockedReason 되찾았으나 확정하지 않은 이유. 확정했거나 못 찾았으면 null.
 */
public record NameVariantOutcome(CatalogCandidate target, String blockedReason) {

    /** 표기를 걷어내도 카탈로그에 같아지는 이름이 없었다. */
    public static final NameVariantOutcome NONE = new NameVariantOutcome(null, null);

    public static NameVariantOutcome unique(CatalogCandidate target) {
        return new NameVariantOutcome(target, null);
    }

    public static NameVariantOutcome blocked(String reason) {
        return new NameVariantOutcome(null, reason);
    }

    public boolean isUnique() {
        return target != null;
    }

    public boolean isBlocked() {
        return blockedReason != null;
    }
}
