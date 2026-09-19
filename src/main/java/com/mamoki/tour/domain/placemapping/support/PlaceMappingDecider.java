package com.mamoki.tour.domain.placemapping.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.mamoki.tour.domain.attraction.support.Coordinates;
import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.placemapping.enums.PlaceMatchMethod;
import com.mamoki.tour.infra.kakao.dto.KakaoPlace;

/**
 * 카카오 검색 결과와 카탈로그 후보를 놓고 이을지 말지 정한다.
 *
 * <p>호출도 저장도 하지 않는 순수 계산이다. 판정 규칙이 배치 진행·재시도·트랜잭션과 섞이면
 * 규칙이 맞는지 확인하려고 매번 DB 와 외부 API 를 세워야 한다.
 *
 * <h2>판정 순서</h2>
 * <ol>
 *   <li>대상 시·군 안의 카카오 장소가 없음 → {@code UNMATCHED}</li>
 *   <li>원천 이름과 똑같은 카카오 장소가 둘 이상 → {@code LOW_CONFIDENCE}
 *       (어느 장소를 가리키는 이름인지 정할 수 없다)</li>
 *   <li>고른 카카오 장소의 대표 이름이 그 시·군 카탈로그에서 <b>유일하게</b> 일치
 *       → {@code CONFIRMED}</li>
 *   <li>거리 경계 안 후보가 한 곳 → {@code CONFIRMED}</li>
 *   <li>거리 경계 안 후보가 둘 이상, 또는 최근접이 경계 밖 → {@code LOW_CONFIDENCE}</li>
 *   <li>좌표를 견줄 후보가 없음 → {@code UNMATCHED}</li>
 * </ol>
 *
 * <p><b>3번을 거리보다 앞에 두는 이유.</b> 스파이크(#4 의 2026-09-18 댓글)에서 이름이 완전히
 * 같은데 거리 때문에 탈락한 것이 미매칭 25건 중 7건이었다. 계곡·산·해변처럼 영역이 넓은 곳은
 * 두 서비스의 대표 좌표가 km 단위로 어긋난다(고원통계곡 13,641m). 경계를 그만큼 넓히면 이번에는
 * 도심에서 다른 장소가 붙는다. 이름 일치를 앞에 두면 경계를 넓히지 않고도 그 7건을 잇는다.
 *
 * <h2>이름 경로의 두 갈래 (#78)</h2>
 * 3번이 안전한 것은 <b>카카오가 원천 이름을 알아본 경우</b>다. 그때는 같은 이름의 카카오
 * 장소가 그 시·군에 하나뿐이라는 검사가 앞에 서 있다.
 *
 * <p>카카오가 원천 이름과 같은 장소를 하나도 주지 않으면 우리는 관련도 1위를 고르는데,
 * 그 장소는 원천 이름과 아무 관계가 없을 수 있다({@code ○○컨트리클럽} 으로 찾았는데
 * {@code 통일전망대} 가 오는 식이다). 그 이름이 카탈로그에 유일하다는 것은 카카오가
 * 엉뚱한 곳을 골랐다는 사실을 조금도 반증하지 않는다. 그래서 이 갈래에서는 이름만으로
 * 확정하지 않고 <b>거리 상한</b>을 함께 본다. 상한은 경계의 몇 배로 두어, 좌표가 km 단위로
 * 어긋나는 넓은 지형은 살리되 수십 km 떨어진 다른 장소는 걸러낸다.
 *
 * <p>"알아봤다" 는 괄호 표기까지 걷어내고 본다. {@code 고원통계곡(상)} 으로 찾아
 * {@code 고원통계곡} 이 온 것은 카카오가 그 장소를 찾아 준 것이다.
 *
 * <p><b>확정을 늘리는 쪽으로 규칙을 더하지 않는다.</b> 경계 안에 후보가 둘이면 그중 하나가
 * 훨씬 가까워도 확정하지 않는다. 틀린 장소에 붙은 입장객 수나 검색순위는 에러를 내지 않아
 * 아무도 알아채지 못한다. 좁히지 못한 것은 좁히지 못한 채로 둔다.
 */
public final class PlaceMappingDecider {

    /**
     * 주소가 강원 밖이면 대상이 아니다.
     *
     * <p>시·군명만 보면 강원 고성군과 경남 고성군이 갈리지 않는다. 카탈로그가 강원 전역만
     * 담는다는 PRD 범위에 기대어 시·도까지 확인한다. 카카오는 {@code 강원특별자치도} 로
     * 내려주지만 옛 표기 {@code 강원도} 도 같이 받는다.
     */
    private static final String PROVINCE_PREFIX = "강원";

    /**
     * 확정한 판정이 가질 수 있는 가장 낮은 신뢰도.
     *
     * <p>경계에 정확히 닿은 거리는 여유가 0 이지만 판정은 확정이다. 그대로 0.000 을 적으면
     * 표를 훑는 사람이 "확정인데 신뢰도 0" 을 보고 확정 규칙을 의심하게 된다. 확정한
     * 것은 0 보다 크게 적어, 0.000 이 확정이 아닌 행만 가리키게 둔다.
     */
    private static final BigDecimal MIN_CONFIRMED_CONFIDENCE = new BigDecimal("0.001");

    private final int boundaryMeters;
    private final int nameBoundaryMeters;

    public PlaceMappingDecider(int boundaryMeters, int nameBoundaryMultiplier) {
        if (boundaryMeters <= 0) {
            throw new IllegalArgumentException("거리 경계는 양수여야 합니다: " + boundaryMeters);
        }

        if (nameBoundaryMultiplier <= 0) {
            throw new IllegalArgumentException(
                    "이름 경로 상한 배수는 양수여야 합니다: " + nameBoundaryMultiplier);
        }

        this.boundaryMeters = boundaryMeters;
        this.nameBoundaryMeters = boundaryMeters * nameBoundaryMultiplier;
    }

    public int boundaryMeters() {
        return boundaryMeters;
    }

    /** 카카오가 원천 이름을 알아보지 못했을 때, 이름만으로 이을 수 있는 최대 거리(m). */
    public int nameBoundaryMeters() {
        return nameBoundaryMeters;
    }

    /**
     * @param sourceName  원천이 적은 이름. 표기 그대로 넘긴다.
     * @param regionName  대상 시·군명({@code 강릉시}). 카카오 결과를 이 시·군으로 좁힌다.
     * @param kakaoPlaces 카카오가 돌려준 장소들. 순서는 카카오의 관련도 순이다.
     * @param catalog     같은 시·군의 카탈로그 후보들.
     */
    public PlaceMappingDecision decide(String sourceName, String regionName,
                                       List<KakaoPlace> kakaoPlaces,
                                       List<CatalogCandidate> catalog) {

        List<KakaoPlace> inRegion = kakaoPlaces.stream()
                .filter(place -> isInRegion(place, regionName))
                .toList();

        if (inRegion.isEmpty()) {
            return PlaceMappingDecision.unmatched(outOfRegionReason(kakaoPlaces, regionName));
        }

        String normalizedSource = PlaceNameNormalizer.normalize(sourceName);
        List<KakaoPlace> sameName = inRegion.stream()
                .filter(place -> sameNormalizedName(place.placeName(), normalizedSource))
                .toList();

        if (sameName.size() > 1) {
            // 이름이 가리키는 장소가 그 시·군 안에서 이미 하나가 아니다. 뒤 단계로 넘어가면
            // 카카오의 관련도 순서가 곧 판단이 되어 버린다.
            return PlaceMappingDecision.lowConfidence(sameName.get(0), null, null, null,
                    "%s 안에 같은 이름의 카카오 장소가 %d 곳입니다.".formatted(regionName, sameName.size()));
        }

        KakaoPlace chosen = sameName.size() == 1 ? sameName.get(0) : inRegion.get(0);
        boolean recognizedSourceName =
                sameName.size() == 1 || sameNameApartFromNotation(chosen, sourceName, regionName);

        PlaceMappingDecision byName = decideByName(chosen, catalog, recognizedSourceName);

        return byName != null ? byName : decideByDistance(chosen, catalog);
    }

    /**
     * 카카오 대표 이름이 카탈로그에서 유일하게 일치하는 경우만 확정한다. 아니면 null 을 주고
     * 거리로 넘긴다.
     *
     * @param recognizedSourceName 카카오가 원천 이름과 같은 장소를 주었는지. 그렇지 않으면
     *                             고른 장소가 원천 이름과 무관할 수 있어 거리 상한을 함께 본다.
     */
    private PlaceMappingDecision decideByName(KakaoPlace chosen, List<CatalogCandidate> catalog,
                                              boolean recognizedSourceName) {
        String normalizedKakao = PlaceNameNormalizer.normalize(chosen.placeName());

        if (normalizedKakao == null) {
            return null;
        }

        List<CatalogCandidate> sameName = catalog.stream()
                .filter(candidate -> normalizedKakao.equals(candidate.normalizedName()))
                .toList();

        if (sameName.size() != 1) {
            return null;
        }

        CatalogCandidate matched = sameName.get(0);
        Double distance = distance(chosen, matched);

        if (!recognizedSourceName && !withinNameBoundary(distance)) {
            // 카카오가 원천 이름을 알아보지 못했고, 고른 장소가 카탈로그 후보에서 멀거나
            // 거리를 아예 견줄 수 없다. 이름이 유일하다는 것만으로는 근거가 되지 않는다.
            return null;
        }

        boolean rawEqual = matched.name() != null && matched.name().equals(chosen.placeName());

        return PlaceMappingDecision.confirmed(chosen, matched.contentId(),
                rawEqual ? PlaceMatchMethod.EXACT : PlaceMatchMethod.NORMALIZED,
                BigDecimal.ONE, distance,
                recognizedSourceName
                        ? "카카오 대표 이름 %s 이(가) 이 시·군 카탈로그에서 유일하게 일치합니다."
                                .formatted(chosen.placeName())
                        : ("카카오가 원천 이름을 알아보지 못했지만 대표 이름 %s 이(가) 카탈로그에서 "
                                + "유일하게 일치하고 %dm 로 상한(%dm) 안입니다.")
                                .formatted(chosen.placeName(), Math.round(distance),
                                        nameBoundaryMeters));
    }

    private boolean withinNameBoundary(Double distance) {
        return distance != null && distance <= nameBoundaryMeters;
    }

    private PlaceMappingDecision decideByDistance(KakaoPlace chosen, List<CatalogCandidate> catalog) {
        if (!chosen.hasCoordinate()) {
            return PlaceMappingDecision.unmatched(chosen,
                    "카카오 장소 %s 에 좌표가 없어 거리로 견줄 수 없습니다.".formatted(chosen.placeName()));
        }

        record Measured(CatalogCandidate candidate, double distance) {
        }

        List<Measured> measured = catalog.stream()
                .filter(CatalogCandidate::hasCoordinate)
                .map(candidate -> new Measured(candidate, distance(chosen, candidate)))
                .sorted((left, right) -> Double.compare(left.distance(), right.distance()))
                .toList();

        if (measured.isEmpty()) {
            // 이 시·군 카탈로그에 좌표가 하나도 없다. 견줄 대상이 없는 것이지 멀다는 뜻이 아니다.
            return PlaceMappingDecision.unmatched(chosen,
                    "좌표를 가진 카탈로그 후보가 이 시·군에 없습니다.");
        }

        List<Measured> withinBoundary = measured.stream()
                .filter(entry -> entry.distance() <= boundaryMeters)
                .toList();

        Measured nearest = measured.get(0);

        if (withinBoundary.isEmpty()) {
            return PlaceMappingDecision.lowConfidence(chosen, nearest.candidate().contentId(),
                    confidenceOf(nearest.distance()), nearest.distance(),
                    "가장 가까운 후보 %s 이(가) %dm 로 경계(%dm) 밖입니다."
                            .formatted(nearest.candidate().name(),
                                    Math.round(nearest.distance()), boundaryMeters));
        }

        if (withinBoundary.size() > 1) {
            return PlaceMappingDecision.lowConfidence(chosen, nearest.candidate().contentId(),
                    confidenceOf(nearest.distance()), nearest.distance(),
                    "경계(%dm) 안 후보가 %d 곳입니다: %s".formatted(boundaryMeters, withinBoundary.size(),
                            withinBoundary.stream().map(entry -> entry.candidate().name()).toList()));
        }

        return PlaceMappingDecision.confirmed(chosen, nearest.candidate().contentId(),
                PlaceMatchMethod.KAKAO_COORD, confirmedConfidenceOf(nearest.distance()),
                nearest.distance(),
                "경계(%dm) 안 후보가 %s 한 곳이고 %dm 떨어져 있습니다."
                        .formatted(boundaryMeters, nearest.candidate().name(),
                                Math.round(nearest.distance())));
    }

    /** 경계까지 남은 여유를 0~1 로 옮긴다. 경계에 닿으면 0, 같은 자리면 1 이다. */
    private BigDecimal confidenceOf(double distanceMeters) {
        double ratio = 1 - (distanceMeters / boundaryMeters);

        return BigDecimal.valueOf(Math.max(0, ratio)).setScale(3, RoundingMode.HALF_UP);
    }

    /** 확정한 판정의 신뢰도. 0.000 이 확정이 아닌 행만 가리키도록 하한을 둔다. */
    private BigDecimal confirmedConfidenceOf(double distanceMeters) {
        return confidenceOf(distanceMeters).max(MIN_CONFIRMED_CONFIDENCE);
    }

    private static Double distance(KakaoPlace place, CatalogCandidate candidate) {
        return Coordinates.distanceMeters(place.latitude(), place.longitude(),
                candidate.latitude(), candidate.longitude());
    }

    /**
     * 카카오가 돌려준 대표 이름이 표기만 걷어내면 원천 이름과 같은가.
     *
     * <p>{@code 고원통계곡(상)} 으로 찾아 {@code 고원통계곡} 이 온 것은 카카오가 그 장소를
     * 찾아 준 것이지 엉뚱한 곳을 고른 것이 아니다. 이 판단에만 매핑 단계 정규화를 쓴다.
     * 앞의 동명 모호성 검사는 공용 정규화 그대로다 — 거기서 규칙을 넓히면 서로 다른 장소가
     * 같은 이름으로 묶여 확정이 늘어난다.
     */
    private static boolean sameNameApartFromNotation(KakaoPlace chosen, String sourceName,
                                                     String regionName) {
        String source = MappingNameNormalizer.normalize(sourceName, regionName);
        String kakao = MappingNameNormalizer.normalize(chosen.placeName(), regionName);

        return source != null && source.equals(kakao);
    }

    private static boolean sameNormalizedName(String placeName, String normalizedSource) {
        return normalizedSource != null
                && normalizedSource.equals(PlaceNameNormalizer.normalize(placeName));
    }

    /**
     * 시·군 안의 결과가 하나도 없을 때의 사유.
     *
     * <p>"시·군 밖" 과 "주소에 시·도가 없어 강원인지 가릴 수 없었다" 는 다른 사실이다.
     * 둘을 같은 문장으로 적으면, 주소 표기가 바뀌어 전부 떨어져 나가는 날에도 로그가
     * 평소와 똑같이 보인다.
     */
    private static String outOfRegionReason(List<KakaoPlace> kakaoPlaces, String regionName) {
        if (kakaoPlaces.isEmpty()) {
            return "카카오 검색 결과가 없습니다.";
        }

        long withProvince = kakaoPlaces.stream().filter(PlaceMappingDecider::hasProvince).count();

        if (withProvince == 0) {
            return "카카오 검색 결과 %d 곳의 주소가 모두 %s 로 시작하지 않아 시·도를 가릴 수 없습니다."
                    .formatted(kakaoPlaces.size(), PROVINCE_PREFIX);
        }

        return "카카오 검색 결과가 모두 %s 밖입니다.".formatted(regionName);
    }

    private static boolean hasProvince(KakaoPlace place) {
        return startsWithProvince(place.addressName()) || startsWithProvince(place.roadAddressName());
    }

    private static boolean startsWithProvince(String address) {
        return address != null && address.startsWith(PROVINCE_PREFIX);
    }

    private static boolean isInRegion(KakaoPlace place, String regionName) {
        return isInRegion(place.addressName(), regionName)
                || isInRegion(place.roadAddressName(), regionName);
    }

    private static boolean isInRegion(String address, String regionName) {
        return address != null && regionName != null
                && address.startsWith(PROVINCE_PREFIX) && address.contains(regionName);
    }
}
