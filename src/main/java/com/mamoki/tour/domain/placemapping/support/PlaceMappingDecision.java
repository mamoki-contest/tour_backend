package com.mamoki.tour.domain.placemapping.support;

import java.math.BigDecimal;

import com.mamoki.tour.domain.placemapping.enums.PlaceMappingStatus;
import com.mamoki.tour.domain.placemapping.enums.PlaceMatchMethod;
import com.mamoki.tour.infra.kakao.dto.KakaoPlace;

/**
 * 원천 이름 하나에 대한 판정 결과.
 *
 * <p>확정하지 못한 경우에도 무엇을 보고 왜 못 정했는지를 함께 담는다. 이 이력이 없으면
 * 다음 실행에서 같은 이름을 또 부르면서도 지난번에 무엇이 걸렸는지 알 수 없다.
 *
 * @param contentId      이어 붙인 카탈로그 식별자. {@code CONFIRMED} 가 아니면 가장 가까웠던
 *                       후보이거나 null 이며, 조회에는 쓰이지 않는다.
 * @param confidence     판정 근거의 여유(0~1). 이름으로 좁힌 경우 1, 거리로 좁힌 경우
 *                       경계까지 남은 여유다. <b>확률이 아니고 판정에도 쓰지 않는다.</b>
 *                       확정 여부는 {@code status} 하나로 정해지며, 이 값은 사람이 나중에
 *                       행을 훑을 때 어느 판정이 아슬아슬했는지 보려고 남긴다.
 * @param distanceMeters 카카오 좌표와 {@code contentId} 좌표 사이 거리(m). 둘 중 하나라도
 *                       좌표가 없으면 null 이다.
 * @param reason         사람이 읽을 판정 근거 한 줄.
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
        String reason
) {

    /** 이을 후보를 하나도 찾지 못했다. 카카오 장소를 고르기도 전에 끝난 경우. */
    public static PlaceMappingDecision unmatched(String reason) {
        return new PlaceMappingDecision(PlaceMappingStatus.UNMATCHED,
                null, null, null, null, null, null, null, null, reason);
    }

    /** 카카오 장소는 골랐지만 이을 카탈로그 후보가 없었다. */
    public static PlaceMappingDecision unmatched(KakaoPlace place, String reason) {
        return new PlaceMappingDecision(PlaceMappingStatus.UNMATCHED,
                null, null, null, place.id(), place.placeName(),
                place.latitude(), place.longitude(), null, reason);
    }

    public static PlaceMappingDecision lowConfidence(KakaoPlace place, String contentId,
                                                     BigDecimal confidence, Double distanceMeters,
                                                     String reason) {
        return new PlaceMappingDecision(PlaceMappingStatus.LOW_CONFIDENCE,
                contentId, null, confidence, place.id(), place.placeName(),
                place.latitude(), place.longitude(), distanceMeters, reason);
    }

    public static PlaceMappingDecision confirmed(KakaoPlace place, String contentId,
                                                 PlaceMatchMethod method, BigDecimal confidence,
                                                 Double distanceMeters, String reason) {
        return new PlaceMappingDecision(PlaceMappingStatus.CONFIRMED,
                contentId, method, confidence, place.id(), place.placeName(),
                place.latitude(), place.longitude(), distanceMeters, reason);
    }

    public boolean isConfirmed() {
        return status == PlaceMappingStatus.CONFIRMED;
    }
}
