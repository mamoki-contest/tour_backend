package com.mamoki.tour.domain.attraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.attraction.support.Coordinates;

/**
 * 두 지점 사이의 거리 계산.
 *
 * <p>기댓값은 이 구현으로 다시 계산한 값이 아니라 밖에서 아는 값이다. 같은 식으로 계산한
 * 값을 기댓값으로 쓰면 식이 틀려도 테스트가 함께 틀려서 아무것도 지키지 못한다.
 *
 * <p>지구를 구로 근사하므로 실제 측지선 거리와는 0.5% 안쪽에서 어긋난다. 이 값은 공급자
 * 간 매칭에서 같은 이름이 같은 장소인지 가리는 데만 쓰여 그 정도면 충분하다.
 */
class CoordinatesTest {

    /** 자오선 1도의 길이. 반지름 6,371km 인 구에서 2πR/360 으로 정해지는 값이다. */
    private static final double ONE_DEGREE_OF_LATITUDE_METERS = 111_194.9;

    @Test
    @DisplayName("위도 1도 차이는 자오선 1도 길이와 같다")
    void oneDegreeOfLatitude() {
        Double distance = Coordinates.distanceMeters(
                new BigDecimal("37.0"), new BigDecimal("128.0"),
                new BigDecimal("38.0"), new BigDecimal("128.0"));

        assertThat(distance).isCloseTo(ONE_DEGREE_OF_LATITUDE_METERS, within(1.0));
    }

    @Test
    @DisplayName("경도 1도 차이는 위도가 높을수록 짧아진다")
    void oneDegreeOfLongitudeShrinksWithLatitude() {
        Double atSeoulLatitude = Coordinates.distanceMeters(
                new BigDecimal("37.5"), new BigDecimal("128.0"),
                new BigDecimal("37.5"), new BigDecimal("129.0"));

        // 위도 37.5도 위도권 둘레는 자오선의 cos(37.5) 배다.
        double expected = ONE_DEGREE_OF_LATITUDE_METERS * Math.cos(Math.toRadians(37.5));

        assertThat(atSeoulLatitude).isCloseTo(expected, within(1.0));
        assertThat(atSeoulLatitude).isLessThan(ONE_DEGREE_OF_LATITUDE_METERS);
    }

    @Test
    @DisplayName("서울시청과 부산시청 사이는 약 325km 다")
    void knownLongDistance() {
        Double distance = Coordinates.distanceMeters(
                new BigDecimal("37.5665"), new BigDecimal("126.9780"),
                new BigDecimal("35.1796"), new BigDecimal("129.0756"));

        assertThat(distance).isCloseTo(325_000, within(2_000.0));
    }

    @Test
    @DisplayName("경포해변과 정동진 사이는 약 17km 다")
    void knownShortDistance() {
        Double distance = Coordinates.distanceMeters(
                new BigDecimal("37.8050"), new BigDecimal("128.9060"),
                new BigDecimal("37.6912"), new BigDecimal("129.0334"));

        assertThat(distance).isCloseTo(16_900, within(200.0));
    }

    @Test
    @DisplayName("같은 지점 사이는 0m 다")
    void samePointIsZero() {
        Double distance = Coordinates.distanceMeters(
                new BigDecimal("37.8050000"), new BigDecimal("128.9060000"),
                new BigDecimal("37.8050000"), new BigDecimal("128.9060000"));

        assertThat(distance).isZero();
    }

    @Test
    @DisplayName("재는 방향을 바꿔도 거리는 같다")
    void isSymmetric() {
        Double forward = Coordinates.distanceMeters(
                new BigDecimal("37.8050"), new BigDecimal("128.9060"),
                new BigDecimal("37.6912"), new BigDecimal("129.0334"));
        Double backward = Coordinates.distanceMeters(
                new BigDecimal("37.6912"), new BigDecimal("129.0334"),
                new BigDecimal("37.8050"), new BigDecimal("128.9060"));

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    @DisplayName("좌표가 하나라도 없으면 거리를 지어내지 않는다")
    void missingCoordinateGivesNull() {
        BigDecimal lat = new BigDecimal("37.8050");
        BigDecimal lon = new BigDecimal("128.9060");

        assertThat(Coordinates.distanceMeters(null, lon, lat, lon)).isNull();
        assertThat(Coordinates.distanceMeters(lat, null, lat, lon)).isNull();
        assertThat(Coordinates.distanceMeters(lat, lon, null, lon)).isNull();
        assertThat(Coordinates.distanceMeters(lat, lon, lat, null)).isNull();
    }

    @Test
    @DisplayName("소수 자릿수를 0 으로 늘려 적어도 같은 값이다")
    void trailingZerosDoNotMatter() {
        Double plain = Coordinates.distanceMeters(
                new BigDecimal("37.8"), new BigDecimal("128.9"),
                new BigDecimal("37.7"), new BigDecimal("128.8"));
        Double padded = Coordinates.distanceMeters(
                new BigDecimal("37.8000000"), new BigDecimal("128.9000000"),
                new BigDecimal("37.7000000"), new BigDecimal("128.8000000"));

        assertThat(plain).isEqualTo(padded);
    }
}
