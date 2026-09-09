package com.mamoki.tour.domain.relatedplace.dto;

/**
 * 한 관광지의 연관 장소를 두 묶음으로 나눈 결과.
 *
 * <p>두 묶음은 성격이 다르다. 대체지 후보는 <b>원래 장소를 대신할</b> 곳이고, 함께 가기 좋은
 * 곳은 <b>같은 여행에서 함께 갈</b> 곳이다. 하나의 목록으로 합치면 프론트가 둘을 구분할 수 없다.
 */
public record RelatedPlaces(RelatedPlacesView alternatives, RelatedPlacesView companions) {

    public static RelatedPlaces noData(String baseYm) {
        return new RelatedPlaces(RelatedPlacesView.noData(baseYm), RelatedPlacesView.noData(baseYm));
    }
}
