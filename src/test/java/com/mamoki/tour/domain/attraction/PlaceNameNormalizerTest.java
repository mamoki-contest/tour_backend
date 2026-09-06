package com.mamoki.tour.domain.attraction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;

class PlaceNameNormalizerTest {

    @Test
    @DisplayName("공백과 구분 기호를 걷어내 표기 차이를 흡수한다")
    void removesSpacingAndSeparators() {
        assertThat(PlaceNameNormalizer.normalize("아르떼뮤지엄/강릉")).isEqualTo("아르떼뮤지엄강릉");
        assertThat(PlaceNameNormalizer.normalize("아르떼뮤지엄 강릉")).isEqualTo("아르떼뮤지엄강릉");
        assertThat(PlaceNameNormalizer.normalize("비발디파크 오션월드")).isEqualTo("비발디파크오션월드");
        assertThat(PlaceNameNormalizer.normalize("강릉 (중앙)시장")).isEqualTo("강릉중앙시장");
    }

    @Test
    @DisplayName("영문 대소문자를 구분하지 않는다")
    void ignoresCase() {
        assertThat(PlaceNameNormalizer.normalize("CGV춘천"))
                .isEqualTo(PlaceNameNormalizer.normalize("cgv 춘천"));
    }

    @Test
    @DisplayName("서로 다른 장소를 같다고 만들지 않는다")
    void keepsDifferentPlacesDistinct() {
        assertThat(PlaceNameNormalizer.normalize("경포해변"))
                .isNotEqualTo(PlaceNameNormalizer.normalize("경포대"));
        assertThat(PlaceNameNormalizer.normalize("낙산사"))
                .isNotEqualTo(PlaceNameNormalizer.normalize("낙산해수욕장"));
    }

    @Test
    @DisplayName("빈 값과 기호만 있는 이름은 null 로 돌려준다")
    void returnsNullForEmptyResult() {
        assertThat(PlaceNameNormalizer.normalize(null)).isNull();
        assertThat(PlaceNameNormalizer.normalize("   ")).isNull();
        assertThat(PlaceNameNormalizer.normalize("///")).isNull();
    }
}
