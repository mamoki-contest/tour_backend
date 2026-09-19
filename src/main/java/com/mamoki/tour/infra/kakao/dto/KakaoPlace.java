package com.mamoki.tour.infra.kakao.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 로컬 키워드 검색이 돌려준 장소 한 곳.
 *
 * <p>좌표가 {@code x}(경도)·{@code y}(위도) 로 오고 둘 다 문자열이다. 뒤집으면 거리 판정이
 * 통째로 어긋나므로 이름 있는 접근자로만 꺼내 쓴다.
 *
 * @param id                 카카오 장소 id. 같은 장소를 다시 찾을 때 쓰는 추적용 값이다.
 * @param placeName          카카오 대표 이름. 원천 이름과 다른 경우가 매핑의 값이다.
 * @param categoryGroupCode  카카오 분류 코드(관광명소 AT4 등). 판정에 쓰지 않고 감사용으로 남긴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoPlace(
        String id,
        @JsonProperty("place_name") String placeName,
        @JsonProperty("category_group_code") String categoryGroupCode,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("address_name") String addressName,
        @JsonProperty("road_address_name") String roadAddressName,
        String x,
        String y
) {

    /** @return 위도. 숫자가 아니면 값을 만들어내지 않고 null 이다. */
    public BigDecimal latitude() {
        return toDecimal(y);
    }

    /** @return 경도. 숫자가 아니면 값을 만들어내지 않고 null 이다. */
    public BigDecimal longitude() {
        return toDecimal(x);
    }

    public boolean hasCoordinate() {
        return latitude() != null && longitude() != null;
    }

    private static BigDecimal toDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
