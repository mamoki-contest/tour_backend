package com.mamoki.tour.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.interest.importer.InterestFileName;
import com.mamoki.tour.domain.interest.importer.InterestImportException;

class InterestFileNameTest {

    @Test
    @DisplayName("파일명에서 시·군과 원천 조회기간을 읽는다")
    void parsesRegionAndPeriod() {
        InterestFileName parsed = InterestFileName.parse(
                "20260906200452_강원특별자치도+강릉시_202508-202607_데이터랩_다운로드.zip");

        assertThat(parsed.sido()).isEqualTo("강원특별자치도");
        assertThat(parsed.sigungu()).isEqualTo("강릉시");
        assertThat(parsed.startYearMonth()).isEqualTo("202508");
        assertThat(parsed.endYearMonth()).isEqualTo("202607");
        assertThat(parsed.sourcePeriod()).isEqualTo("202508-202607");
    }

    @Test
    @DisplayName("군 단위 시·군명도 읽는다")
    void parsesGunName() {
        assertThat(InterestFileName.parse(
                "20260906200458_강원특별자치도+고성군_202508-202607_데이터랩_다운로드.zip").sigungu())
                .isEqualTo("고성군");
    }

    @Test
    @DisplayName("형식이 다른 파일명은 거부한다")
    void rejectsUnexpectedFormat() {
        assertThatThrownBy(() -> InterestFileName.parse("관심도.zip"))
                .isInstanceOf(InterestImportException.class)
                .hasMessageContaining("파일명 형식");

        assertThatThrownBy(() -> InterestFileName.parse(
                "20260906200452_강원특별자치도_202508-202607_데이터랩_다운로드.zip"))
                .isInstanceOf(InterestImportException.class);

        assertThatThrownBy(() -> InterestFileName.parse(
                "20260906200452_강원특별자치도+강릉시_2025-2026_데이터랩_다운로드.zip"))
                .isInstanceOf(InterestImportException.class);
    }
}
