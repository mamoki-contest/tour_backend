package com.mamoki.tour.domain.placemapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.placemapping.enums.PlaceMappingStatus;
import com.mamoki.tour.domain.placemapping.enums.PlaceMatchMethod;
import com.mamoki.tour.domain.placemapping.support.CatalogCandidate;
import com.mamoki.tour.domain.placemapping.support.PlaceMappingDecider;
import com.mamoki.tour.domain.placemapping.support.PlaceMappingDecision;
import com.mamoki.tour.infra.kakao.dto.KakaoPlace;

/**
 * 원천 이름 하나를 카탈로그에 이을지 말지 정하는 규칙.
 *
 * <p>여기서 잘못 확정하면 엉뚱한 관광지에 입장객 수나 검색순위가 붙는데, 에러가 나지 않아
 * 아무도 알아채지 못한다. 그래서 확정 조건을 좁히는 쪽으로만 테스트를 둔다.
 */
class PlaceMappingDeciderTest {

    private static final String REGION = "강릉시";

    /** 경포해수욕장의 실제 카카오 좌표. 위도 1도는 약 111km 라 0.0009 도가 약 100m 다. */
    private static final BigDecimal BASE_LATITUDE = new BigDecimal("37.8034");
    private static final BigDecimal BASE_LONGITUDE = new BigDecimal("128.9102");

    private final PlaceMappingDecider decider = new PlaceMappingDecider(300);

    private static KakaoPlace kakaoPlace(String id, String name, String latitude, String longitude) {
        return new KakaoPlace(id, name, "AT4", "여행 > 관광,명소",
                "강원특별자치도 " + REGION + " 강문동 산 1", "강원특별자치도 " + REGION + " 창해로 514",
                longitude, latitude);
    }

    private static KakaoPlace nearbyPlace(String name) {
        return kakaoPlace("8199114", name,
                BASE_LATITUDE.toPlainString(), BASE_LONGITUDE.toPlainString());
    }

    /** 기준 좌표에서 북쪽으로 대략 (metersNorth) m 떨어진 카탈로그 후보. */
    private static CatalogCandidate candidate(String contentId, String name, double metersNorth) {
        return new CatalogCandidate(contentId, name,
                BASE_LATITUDE.add(BigDecimal.valueOf(metersNorth / 111_195.0)), BASE_LONGITUDE);
    }

    @Test
    @DisplayName("경계 안에 후보가 한 곳이면 확정한다")
    void confirmsSingleCandidateWithinBoundary() {
        PlaceMappingDecision decision = decider.decide("강릉 경포해변", REGION,
                List.of(nearbyPlace("경포해수욕장")),
                List.of(candidate("125266", "경포호", 100)));

        assertThat(decision.status()).isEqualTo(PlaceMappingStatus.CONFIRMED);
        assertThat(decision.contentId()).isEqualTo("125266");
        assertThat(decision.method()).isEqualTo(PlaceMatchMethod.KAKAO_COORD);
        assertThat(decision.distanceMeters()).isCloseTo(100, org.assertj.core.data.Offset.offset(2.0));
        assertThat(decision.kakaoPlaceId()).isEqualTo("8199114");
    }

    @Test
    @DisplayName("경계 안에 후보가 둘 이상이면 더 가까운 쪽이 있어도 확정하지 않는다")
    void doesNotConfirmWhenSeveralCandidatesAreWithinBoundary() {
        PlaceMappingDecision decision = decider.decide("강릉 경포해변", REGION,
                List.of(nearbyPlace("경포해수욕장")),
                List.of(candidate("125266", "경포호", 250), candidate("125287", "경포대", 20)));

        assertThat(decision.status()).isEqualTo(PlaceMappingStatus.LOW_CONFIDENCE);
        assertThat(decision.method()).isNull();
        assertThat(decision.reason()).contains("경계(300m) 안 후보가 2 곳");
    }

    @Test
    @DisplayName("가장 가까운 후보가 경계 밖이면 확정하지 않되 무엇이 가까웠는지는 남긴다")
    void doesNotConfirmWhenNearestIsOutsideBoundary() {
        PlaceMappingDecision decision = decider.decide("강릉 경포해변", REGION,
                List.of(nearbyPlace("경포해수욕장")),
                List.of(candidate("125266", "경포호", 900)));

        assertThat(decision.status()).isEqualTo(PlaceMappingStatus.LOW_CONFIDENCE);
        assertThat(decision.contentId()).isEqualTo("125266");
        assertThat(decision.distanceMeters()).isCloseTo(900, org.assertj.core.data.Offset.offset(5.0));
        assertThat(decision.confidence()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("카탈로그에 좌표가 없으면 멀다고 하지 않고 이을 후보가 없다고 남긴다")
    void reportsNoCandidateWhenCatalogHasNoCoordinate() {
        PlaceMappingDecision decision = decider.decide("강릉 경포해변", REGION,
                List.of(nearbyPlace("경포해수욕장")),
                List.of(new CatalogCandidate("125266", "경포호", null, null)));

        assertThat(decision.status()).isEqualTo(PlaceMappingStatus.UNMATCHED);
        assertThat(decision.contentId()).isNull();
        assertThat(decision.reason()).contains("좌표를 가진 카탈로그 후보");
        // 무엇을 찾았는지는 남는다. 다음 실행이 같은 이름을 또 부르기 전에 볼 단서다.
        assertThat(decision.kakaoPlaceName()).isEqualTo("경포해수욕장");
    }

    @Test
    @DisplayName("카카오가 아무것도 찾지 못하면 미매칭이다")
    void unmatchedWhenKakaoFindsNothing() {
        PlaceMappingDecision decision = decider.decide("있을리없는이름", REGION,
                List.of(), List.of(candidate("125266", "경포호", 10)));

        assertThat(decision.status()).isEqualTo(PlaceMappingStatus.UNMATCHED);
        assertThat(decision.reason()).contains("카카오 검색 결과가 없습니다");
    }

    @Test
    @DisplayName("찾은 장소가 모두 다른 시·군이면 그 시·군 장소로 삼지 않는다")
    void unmatchedWhenEveryPlaceIsOutsideTheRegion() {
        KakaoPlace otherRegion = new KakaoPlace("1", "경포해수욕장", "AT4", null,
                "강원특별자치도 속초시 조양동 1", null, "128.5911", "38.1912");

        PlaceMappingDecision decision = decider.decide("강릉 경포해변", REGION,
                List.of(otherRegion), List.of(candidate("125266", "경포호", 10)));

        assertThat(decision.status()).isEqualTo(PlaceMappingStatus.UNMATCHED);
        assertThat(decision.reason()).contains("강릉시 밖");
    }

    @Test
    @DisplayName("다른 시·도의 같은 이름 시·군에 붙지 않는다")
    void doesNotMatchSameSigunguNameInAnotherProvince() {
        KakaoPlace gyeongnam = new KakaoPlace("1", "당항포", "AT4", null,
                "경상남도 고성군 회화면 당항리", null, "128.3266", "35.0567");

        PlaceMappingDecision decision = decider.decide("당항포관광지", "고성군",
                List.of(gyeongnam), List.of(candidate("125266", "당항포", 10)));

        assertThat(decision.status()).isEqualTo(PlaceMappingStatus.UNMATCHED);
    }

    @Test
    @DisplayName("카카오 대표 이름이 카탈로그와 유일하게 일치하면 멀어도 확정한다")
    void confirmsByNameEvenWhenFarApart() {
        // 계곡·산·해변은 두 서비스의 대표 좌표가 km 단위로 어긋난다. 이름이 그 시·군에서
        // 유일하면 거리보다 이름이 확실한 근거다 (#4 스파이크: 고원통계곡 13,641m).
        PlaceMappingDecision decision = decider.decide("고원통계곡(상)", REGION,
                List.of(nearbyPlace("고원통계곡")),
                List.of(candidate("125266", "고원통계곡", 13_641), candidate("125287", "경포대", 5_000)));

        assertThat(decision.status()).isEqualTo(PlaceMappingStatus.CONFIRMED);
        assertThat(decision.contentId()).isEqualTo("125266");
        assertThat(decision.method()).isEqualTo(PlaceMatchMethod.EXACT);
        assertThat(decision.confidence()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("표기만 다른 이름 일치는 정규화 매칭으로 구분해 남긴다")
    void recordsNormalizedMatchSeparately() {
        PlaceMappingDecision decision = decider.decide("아르떼뮤지엄강릉", REGION,
                List.of(nearbyPlace("아르떼뮤지엄 강릉")),
                List.of(candidate("125266", "아르떼뮤지엄/강릉", 4_000)));

        assertThat(decision.status()).isEqualTo(PlaceMappingStatus.CONFIRMED);
        assertThat(decision.method()).isEqualTo(PlaceMatchMethod.NORMALIZED);
    }

    @Test
    @DisplayName("이름이 카탈로그에서 유일하지 않으면 이름으로 확정하지 않고 거리로 넘어간다")
    void fallsBackToDistanceWhenNameIsNotUnique() {
        PlaceMappingDecision decision = decider.decide("강릉 경포해변", REGION,
                List.of(nearbyPlace("경포해수욕장")),
                List.of(candidate("125266", "경포해수욕장", 50),
                        candidate("125287", "경포 해수욕장", 9_000)));

        // 이름은 둘 다 일치하지만 거리 경계 안에는 한 곳뿐이라 거리로 좁혀진다.
        assertThat(decision.status()).isEqualTo(PlaceMappingStatus.CONFIRMED);
        assertThat(decision.contentId()).isEqualTo("125266");
        assertThat(decision.method()).isEqualTo(PlaceMatchMethod.KAKAO_COORD);
    }

    @Test
    @DisplayName("원천 이름과 똑같은 카카오 장소가 둘이면 어느 장소인지 정하지 않는다")
    void doesNotChooseAmongKakaoPlacesWithTheSameName() {
        PlaceMappingDecision decision = decider.decide("주문진해변", REGION,
                List.of(kakaoPlace("1", "주문진해변", "37.8900", "128.8300"),
                        kakaoPlace("2", "주문진해변", "37.8950", "128.8350")),
                List.of(candidate("125266", "주문진해변", 10)));

        assertThat(decision.status()).isEqualTo(PlaceMappingStatus.LOW_CONFIDENCE);
        assertThat(decision.reason()).contains("같은 이름의 카카오 장소가 2 곳");
    }

    @Test
    @DisplayName("이름이 겹치지 않으면 카카오 관련도 1위 장소의 좌표를 쓴다")
    void usesTheMostRelevantPlaceWhenNoNameMatches() {
        PlaceMappingDecision decision = decider.decide("강릉 경포해변", REGION,
                List.of(nearbyPlace("경포해수욕장"),
                        kakaoPlace("2", "경포해변 주차장", "37.8060", "128.9062")),
                List.of(candidate("125266", "경포호", 80)));

        assertThat(decision.status()).isEqualTo(PlaceMappingStatus.CONFIRMED);
        assertThat(decision.kakaoPlaceId()).isEqualTo("8199114");
    }
}
