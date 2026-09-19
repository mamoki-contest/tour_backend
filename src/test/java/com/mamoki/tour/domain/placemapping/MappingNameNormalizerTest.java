package com.mamoki.tour.domain.placemapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.placemapping.support.MappingNameNormalizer;

/**
 * 매핑 단계 전용 표기 정규화.
 *
 * <p>{@link PlaceNameNormalizer} 는 언급량 수집·TMAP·입장객 매칭이 모두 쓰는 규칙이라
 * 건드리지 않는다. 여기서만 괄호 안 별칭과 시·군 접두어를 한 겹 더 걷어낸다. 그 규칙을
 * 공용 정규화에 넣으면 검색어 규칙 버전이 바뀌면서 지난 달 언급량과 비교할 수 없게 된다.
 */
class MappingNameNormalizerTest {

    @Test
    @DisplayName("괄호 안 별칭을 걷어낸다")
    void stripsParenthesizedAlias() {
        assertThat(MappingNameNormalizer.normalize("사근진해변(사근진해수욕장)", "강릉시"))
                .isEqualTo(PlaceNameNormalizer.normalize("사근진해변"));
    }

    @Test
    @DisplayName("대괄호와 전각 괄호도 같게 본다")
    void stripsOtherBrackets() {
        assertThat(MappingNameNormalizer.normalize("영월 장릉(단종) [유네스코 세계유산]", "영월군"))
                .isEqualTo(PlaceNameNormalizer.normalize("장릉"));
    }

    @Test
    @DisplayName("시·군 접두어를 걷어낸다 — 시·군명 그대로와 접미사를 뗀 형태 둘 다")
    void stripsRegionPrefix() {
        assertThat(MappingNameNormalizer.normalize("강릉 오죽헌·시립박물관", "강릉시"))
                .isEqualTo(MappingNameNormalizer.normalize("오죽헌.시립박물관", "강릉시"));
        assertThat(MappingNameNormalizer.normalize("춘천시 수변공원", "춘천시"))
                .isEqualTo(MappingNameNormalizer.normalize("춘천수변공원", "춘천시"));
    }

    @Test
    @DisplayName("시·도 접두어도 걷어낸다 — 강원도와 강원특별자치도가 같아진다")
    void stripsProvincePrefix() {
        assertThat(MappingNameNormalizer.normalize("강원도립화목원", "춘천시"))
                .isEqualTo(MappingNameNormalizer.normalize("강원특별자치도립화목원", "춘천시"));
    }

    @Test
    @DisplayName("접두어만 남는 이름은 값으로 쓰지 않는다")
    void rejectsNameThatIsOnlyPrefix() {
        assertThat(MappingNameNormalizer.normalize("강릉시", "강릉시")).isNull();
        assertThat(MappingNameNormalizer.normalize("(폐역)", "춘천시")).isNull();
    }

    @Test
    @DisplayName("한 글자만 남는 이름도 값으로 쓰지 않는다 — 걷어내다 다른 장소와 겹친다")
    void rejectsSingleCharacterResult() {
        assertThat(MappingNameNormalizer.normalize("강원도청", "춘천시")).isNull();
    }

    @Test
    @DisplayName("걷어낼 것이 없으면 공용 정규화와 같은 값이다")
    void fallsBackToSharedNormalization() {
        assertThat(MappingNameNormalizer.normalize("경포해수욕장", "강릉시"))
                .isEqualTo(PlaceNameNormalizer.normalize("경포해수욕장"));
    }

    @Test
    @DisplayName("시·군을 모르면 시·군 접두어는 건드리지 않는다")
    void keepsRegionPrefixWhenRegionUnknown() {
        assertThat(MappingNameNormalizer.normalize("강릉 동부시장", null))
                .isEqualTo(PlaceNameNormalizer.normalize("강릉 동부시장"));
    }

    @Test
    @DisplayName("null 은 null 이다")
    void nullStaysNull() {
        assertThat(MappingNameNormalizer.normalize(null, "강릉시")).isNull();
    }
}
