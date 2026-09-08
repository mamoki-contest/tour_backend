package com.mamoki.tour.domain.relatedplace.support;

import com.mamoki.tour.domain.relatedplace.enums.RelatedPlaceKind;

/**
 * 공급자 대분류를 연관 장소 유형으로 옮긴다.
 *
 * <p>실제 응답(강릉시 51150, 2026-07 기준)에서 확인된 대분류는 {@code 관광지} · {@code 음식} ·
 * {@code 숙박} 세 가지다. 그 밖의 값은 {@link RelatedPlaceKind#OTHER} 로 남기고 관광지로
 * 넘겨짚지 않는다. 모르는 값을 관광지로 취급하면 대체지가 될 수 없는 곳이 대체지 후보로
 * 올라간다.
 */
public final class RelatedPlaceClassifier {

    private static final String ATTRACTION = "관광지";
    private static final String RESTAURANT = "음식";
    private static final String LODGING = "숙박";

    private RelatedPlaceClassifier() {
    }

    /**
     * @param categoryLarge 공급자 대분류({@code rlteCtgryLclsNm}). 결측이면 null 이 올 수 있다.
     */
    public static RelatedPlaceKind classify(String categoryLarge) {
        if (categoryLarge == null) {
            return RelatedPlaceKind.OTHER;
        }

        return switch (categoryLarge.trim()) {
            case ATTRACTION -> RelatedPlaceKind.ATTRACTION;
            case RESTAURANT -> RelatedPlaceKind.RESTAURANT;
            case LODGING -> RelatedPlaceKind.LODGING;
            default -> RelatedPlaceKind.OTHER;
        };
    }

    /** 함께 가기 좋은 곳은 음식점과 숙박시설이다. 유형을 모르는 곳은 넣지 않는다. */
    public static boolean isCompanion(RelatedPlaceKind kind) {
        return kind == RelatedPlaceKind.RESTAURANT || kind == RelatedPlaceKind.LODGING;
    }
}
