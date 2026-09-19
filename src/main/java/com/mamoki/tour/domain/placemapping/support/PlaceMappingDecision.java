package com.mamoki.tour.domain.placemapping.support;

import java.math.BigDecimal;

import com.mamoki.tour.domain.placemapping.enums.PlaceMappingStatus;
import com.mamoki.tour.domain.placemapping.enums.PlaceMatchMethod;
import com.mamoki.tour.domain.placemapping.enums.UnmatchedCategory;
import com.mamoki.tour.infra.kakao.dto.KakaoPlace;

/**
 * 원천 이름 하나에 대한 판정 결과.
 *
 * <p>확정하지 못한 경우에도 무엇을 보고 왜 못 정했는지를 함께 담는다. 이 이력이 없으면
 * 다음 실행에서 같은 이름을 또 부르면서도 지난번에 무엇이 걸렸는지 알 수 없다.
 *
 * @param contentId              이어 붙인 카탈로그 식별자. {@code CONFIRMED} 가 아니면 가장
 *                               가까웠던 후보이거나 null 이며, 조회에는 쓰이지 않는다.
 * @param confidence             판정 근거의 여유(0~1). 이름으로 좁힌 경우 1, 거리로 좁힌 경우
 *                               경계까지 남은 여유다. <b>확률이 아니고 판정에도 쓰지 않는다.</b>
 *                               확정 여부는 {@code status} 하나로 정해지며, 이 값은 사람이
 *                               나중에 행을 훑을 때 어느 판정이 아슬아슬했는지 보려고 남긴다.
 * @param distanceMeters         카카오 좌표와 {@code contentId} 좌표 사이 거리(m). 둘 중
 *                               하나라도 좌표가 없으면 null 이다.
 * @param reason                 사람이 읽을 판정 근거 한 줄.
 * @param kakaoCategoryGroupCode 카카오 분류 코드. 판정에 쓰지 않고 미매칭 재분류(#72)의
 *                               근거와 감사용으로 남긴다. 비어 오는 장소가 많다.
 * @param kakaoCategoryName      카카오 분류 이름. {@code 스포츠,레저 > 골프 > 골프장} 처럼 온다.
 * @param category               잇지 못한 이유의 분류. 확정한 행에는 표기 차이로 되찾았을
 *                               때만 붙고, 그 밖에는 null 이다.
 * @param categoryRule           {@code OUT_OF_CATALOG} 로 본 카카오 카테고리 규칙.
 * @param nameSuffixRule         {@code OUT_OF_CATALOG} 로 본 이름 접미어 규칙.
 */
public record PlaceMappingDecision(
        PlaceMappingStatus status,
        String contentId,
        PlaceMatchMethod method,
        BigDecimal confidence,
        String kakaoPlaceId,
        String kakaoPlaceName,
        BigDecimal kakaoLatitude,
        BigDecimal kakaoLongitude,
        Double distanceMeters,
        String reason,
        String kakaoCategoryGroupCode,
        String kakaoCategoryName,
        UnmatchedCategory category,
        String categoryRule,
        String nameSuffixRule
) {

    /** 이을 후보를 하나도 찾지 못했다. 카카오 장소를 고르기도 전에 끝난 경우. */
    public static PlaceMappingDecision unmatched(String reason) {
        return new PlaceMappingDecision(PlaceMappingStatus.UNMATCHED,
                null, null, null, null, null, null, null, null, reason,
                null, null, null, null, null);
    }

    /** 카카오 장소는 골랐지만 이을 카탈로그 후보가 없었다. */
    public static PlaceMappingDecision unmatched(KakaoPlace place, String reason) {
        return new PlaceMappingDecision(PlaceMappingStatus.UNMATCHED,
                null, null, null, place.id(), place.placeName(),
                place.latitude(), place.longitude(), null, reason,
                place.categoryGroupCode(), place.categoryName(), null, null, null);
    }

    public static PlaceMappingDecision lowConfidence(KakaoPlace place, String contentId,
                                                     BigDecimal confidence, Double distanceMeters,
                                                     String reason) {
        return new PlaceMappingDecision(PlaceMappingStatus.LOW_CONFIDENCE,
                contentId, null, confidence, place.id(), place.placeName(),
                place.latitude(), place.longitude(), distanceMeters, reason,
                place.categoryGroupCode(), place.categoryName(), null, null, null);
    }

    public static PlaceMappingDecision confirmed(KakaoPlace place, String contentId,
                                                 PlaceMatchMethod method, BigDecimal confidence,
                                                 Double distanceMeters, String reason) {
        return new PlaceMappingDecision(PlaceMappingStatus.CONFIRMED,
                contentId, method, confidence, place.id(), place.placeName(),
                place.latitude(), place.longitude(), distanceMeters, reason,
                place.categoryGroupCode(), place.categoryName(), null, null, null);
    }

    public boolean isConfirmed() {
        return status == PlaceMappingStatus.CONFIRMED;
    }

    /**
     * 표기 차이로 카탈로그를 되찾아 확정으로 올린다.
     *
     * <p>카카오가 무엇을 돌려주었는지는 그대로 들고 간다. 나중에 이 확정이 의심스러울 때
     * 카카오가 본 장소와 우리가 이은 장소를 나란히 놓고 볼 수 있어야 한다.
     */
    public PlaceMappingDecision promotedToNameVariant(String contentId, String reason) {
        return new PlaceMappingDecision(PlaceMappingStatus.CONFIRMED,
                contentId, PlaceMatchMethod.NAME_VARIANT, BigDecimal.ONE,
                kakaoPlaceId, kakaoPlaceName, kakaoLatitude, kakaoLongitude, null, reason,
                kakaoCategoryGroupCode, kakaoCategoryName, UnmatchedCategory.NAME_VARIANT,
                null, null);
    }

    /**
     * 표기 차이로 되찾긴 했으나 한 곳으로 좁히지 못했다.
     *
     * <p>{@code contentId} 를 비운다. 가장 그럴듯한 후보를 남겨 두면 다음에 누군가
     * 그 값을 쓰게 된다. 좁히지 못한 것은 좁히지 못한 채로 둔다.
     */
    public PlaceMappingDecision ambiguousNameVariant(String reason) {
        return new PlaceMappingDecision(PlaceMappingStatus.LOW_CONFIDENCE,
                null, null, null, kakaoPlaceId, kakaoPlaceName,
                kakaoLatitude, kakaoLongitude, null, reason,
                kakaoCategoryGroupCode, kakaoCategoryName, UnmatchedCategory.NAME_VARIANT,
                null, null);
    }

    /** 잇지 못한 이유의 분류만 덧붙인다. 상태·연결 대상은 그대로 둔다. */
    public PlaceMappingDecision classifiedAs(UnmatchedCategory category,
                                             String categoryRule, String nameSuffixRule) {
        return new PlaceMappingDecision(status, contentId, method, confidence,
                kakaoPlaceId, kakaoPlaceName, kakaoLatitude, kakaoLongitude, distanceMeters,
                reason, kakaoCategoryGroupCode, kakaoCategoryName,
                category, categoryRule, nameSuffixRule);
    }
}
