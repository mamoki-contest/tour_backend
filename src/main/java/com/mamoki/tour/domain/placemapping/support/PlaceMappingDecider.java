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
 * 같은 시·군에 같은 이름이 둘 있으면 유일성 조건에서 걸러지므로, 이 길이 거리보다 느슨하지 않다.
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

    private final int boundaryMeters;

    public PlaceMappingDecider(int boundaryMeters) {
        if (boundaryMeters <= 0) {
            throw new IllegalArgumentException("거리 경계는 양수여야 합니다: " + boundaryMeters);
        }

        this.boundaryMeters = boundaryMeters;
    }

    public int boundaryMeters() {
        return boundaryMeters;
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
            return PlaceMappingDecision.unmatched(kakaoPlaces.isEmpty()
                    ? "카카오 검색 결과가 없습니다."
                    : "카카오 검색 결과가 모두 %s 밖입니다.".formatted(regionName));
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

        PlaceMappingDecision byName = decideByName(chosen, catalog);

        return byName != null ? byName : decideByDistance(chosen, catalog);
    }

    /** 카카오 대표 이름이 카탈로그에서 유일하게 일치하는 경우만 확정한다. 아니면 null 을 주고 거리로 넘긴다. */
    private PlaceMappingDecision decideByName(KakaoPlace chosen, List<CatalogCandidate> catalog) {
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
        boolean rawEqual = matched.name() != null && matched.name().equals(chosen.placeName());

        return PlaceMappingDecision.confirmed(chosen, matched.contentId(),
                rawEqual ? PlaceMatchMethod.EXACT : PlaceMatchMethod.NORMALIZED,
                BigDecimal.ONE, distance(chosen, matched),
                "카카오 대표 이름 %s 이(가) 이 시·군 카탈로그에서 유일하게 일치합니다."
                        .formatted(chosen.placeName()));
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
                PlaceMatchMethod.KAKAO_COORD, confidenceOf(nearest.distance()), nearest.distance(),
                "경계(%dm) 안 후보가 %s 한 곳이고 %dm 떨어져 있습니다."
                        .formatted(boundaryMeters, nearest.candidate().name(),
                                Math.round(nearest.distance())));
    }

    /** 경계까지 남은 여유를 0~1 로 옮긴다. 경계에 닿으면 0, 같은 자리면 1 이다. */
    private BigDecimal confidenceOf(double distanceMeters) {
        double ratio = 1 - (distanceMeters / boundaryMeters);

        return BigDecimal.valueOf(Math.max(0, ratio)).setScale(3, RoundingMode.HALF_UP);
    }

    private static Double distance(KakaoPlace place, CatalogCandidate candidate) {
        return Coordinates.distanceMeters(place.latitude(), place.longitude(),
                candidate.latitude(), candidate.longitude());
    }

    private static boolean sameNormalizedName(String placeName, String normalizedSource) {
        return normalizedSource != null
                && normalizedSource.equals(PlaceNameNormalizer.normalize(placeName));
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
