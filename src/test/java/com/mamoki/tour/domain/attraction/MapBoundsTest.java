package com.mamoki.tour.domain.attraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.attraction.dto.AttractionSearchRequest;
import com.mamoki.tour.domain.attraction.support.MapBounds;

/** 지도 경계 판정과 요청 검증 규칙. */
class MapBoundsTest {

    /** 강릉 일대를 감싸는 경계. */
    private static final MapBounds GANGNEUNG = new MapBounds(
            new BigDecimal("37.6"), new BigDecimal("37.9"),
            new BigDecimal("128.7"), new BigDecimal("129.0"));

    private static AttractionSearchRequest requestWithBounds(String minLat, String maxLat,
                                                             String minLng, String maxLng) {
        return new AttractionSearchRequest(null, null, null, null, null, null, null,
                minLat == null ? null : new BigDecimal(minLat),
                maxLat == null ? null : new BigDecimal(maxLat),
                minLng == null ? null : new BigDecimal(minLng),
                maxLng == null ? null : new BigDecimal(maxLng));
    }

    @Test
    @DisplayName("경계 안의 좌표를 담는다")
    void containsPointInside() {
        assertThat(GANGNEUNG.contains(new BigDecimal("37.7952"), new BigDecimal("128.8961")))
                .isTrue();
    }

    @Test
    @DisplayName("경계 밖의 좌표는 담지 않는다")
    void excludesPointOutside() {
        // 춘천. 경계보다 서쪽이다.
        assertThat(GANGNEUNG.contains(new BigDecimal("37.8813"), new BigDecimal("127.7300")))
                .isFalse();
    }

    @Test
    @DisplayName("경계선 위의 좌표는 안쪽으로 본다")
    void includesPointOnEdge() {
        assertThat(GANGNEUNG.contains(new BigDecimal("37.6"), new BigDecimal("128.7"))).isTrue();
        assertThat(GANGNEUNG.contains(new BigDecimal("37.9"), new BigDecimal("129.0"))).isTrue();
    }

    @Test
    @DisplayName("좌표가 없는 장소는 경계 안이라고 단정하지 않는다")
    void excludesMissingCoordinates() {
        assertThat(GANGNEUNG.contains(null, new BigDecimal("128.8"))).isFalse();
        assertThat(GANGNEUNG.contains(new BigDecimal("37.7"), null)).isFalse();
        assertThat(GANGNEUNG.contains(null, null)).isFalse();
    }

    @Test
    @DisplayName("네 값을 모두 주면 경계를 만든다")
    void buildsBoundsWhenComplete() {
        AttractionSearchRequest request = requestWithBounds("37.6", "37.9", "128.7", "129.0");

        assertThat(request.hasBounds()).isTrue();
        assertThat(request.isBoundsCompleteOrAbsent()).isTrue();
        assertThat(request.isBoundsOrdered()).isTrue();
        assertThat(request.bounds()).isEqualTo(GANGNEUNG);
    }

    @Test
    @DisplayName("경계를 주지 않으면 전체 범위 경계를 만들어내지 않는다")
    void noBoundsWhenAbsent() {
        AttractionSearchRequest request = requestWithBounds(null, null, null, null);

        assertThat(request.hasBounds()).isFalse();
        assertThat(request.isBoundsCompleteOrAbsent()).isTrue();
        assertThat(request.bounds()).isNull();
    }

    @Test
    @DisplayName("일부만 준 경계는 나머지를 채우지 않고 거절한다")
    void rejectsPartialBounds() {
        AttractionSearchRequest request = requestWithBounds("37.6", "37.9", "128.7", null);

        assertThat(request.isBoundsCompleteOrAbsent()).isFalse();
        assertThat(request.hasBounds()).isFalse();
    }

    @Test
    @DisplayName("최솟값이 최댓값보다 크면 거절한다")
    void rejectsInvertedBounds() {
        assertThat(requestWithBounds("37.9", "37.6", "128.7", "129.0").isBoundsOrdered()).isFalse();
        assertThat(requestWithBounds("37.6", "37.9", "129.0", "128.7").isBoundsOrdered()).isFalse();
    }
}
