package com.mamoki.tour.domain.placemapping;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 장소 매핑 판정 설정.
 *
 * @param boundaryMeters         카카오 좌표와 카탈로그 좌표가 이 거리 안이면 같은 장소로 본다.
 * @param maxCallsPerRun         한 번의 실행에서 낼 수 있는 카카오 호출 수의 상한. 원천 이름
 *                               수가 예상 밖으로 늘어도 하루 한도(100,000회)를 한 번에
 *                               소진하지 않게 막는다.
 * @param nameBoundaryMultiplier 카카오가 원천 이름을 알아보지 못한 채 대표 이름만으로 이을
 *                               때 쓰는 거리 상한의 배수(#78). {@code boundaryMeters} 에
 *                               곱한다. 그 갈래에서는 카카오가 고른 장소가 원천 이름과 무관할
 *                               수 있어, 이름이 유일하다는 것만으로 확정하면 수십 km 떨어진
 *                               다른 관광지에 값이 붙는다.
 */
@ConfigurationProperties(prefix = "tour.place-mapping")
public record PlaceMappingProperties(int boundaryMeters, int maxCallsPerRun,
                                     int nameBoundaryMultiplier) {

    private static final int DEFAULT_BOUNDARY_METERS = 300;
    private static final int DEFAULT_MAX_CALLS_PER_RUN = 20_000;

    /**
     * 기본 5배(경계 300m 기준 1,500m).
     *
     * <p>#68 이 이 갈래로 이은 대표 사례가 {@code 대관령자연휴양림 → 국립대관령자연휴양림}
     * 469m 다. 그보다는 넉넉하고, 시·군 하나를 가로지르는 거리보다는 좁다. 이름이 완전히
     * 같아 상한 없이 잇는 넓은 지형(고원통계곡 13,641m)은 이 상한을 거치지 않는다.
     */
    private static final int DEFAULT_NAME_BOUNDARY_MULTIPLIER = 5;

    public PlaceMappingProperties {
        boundaryMeters = boundaryMeters > 0 ? boundaryMeters : DEFAULT_BOUNDARY_METERS;
        maxCallsPerRun = maxCallsPerRun > 0 ? maxCallsPerRun : DEFAULT_MAX_CALLS_PER_RUN;
        nameBoundaryMultiplier = nameBoundaryMultiplier > 0
                ? nameBoundaryMultiplier : DEFAULT_NAME_BOUNDARY_MULTIPLIER;
    }
}
