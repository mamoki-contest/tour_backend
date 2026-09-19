package com.mamoki.tour.domain.placemapping;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 장소 매핑 판정 설정.
 *
 * @param boundaryMeters  카카오 좌표와 카탈로그 좌표가 이 거리 안이면 같은 장소로 본다.
 * @param maxCallsPerRun  한 번의 실행에서 낼 수 있는 카카오 호출 수의 상한. 원천 이름 수가
 *                        예상 밖으로 늘어도 하루 한도(100,000회)를 한 번에 소진하지 않게 막는다.
 */
@ConfigurationProperties(prefix = "tour.place-mapping")
public record PlaceMappingProperties(int boundaryMeters, int maxCallsPerRun) {

    private static final int DEFAULT_BOUNDARY_METERS = 300;
    private static final int DEFAULT_MAX_CALLS_PER_RUN = 20_000;

    public PlaceMappingProperties {
        boundaryMeters = boundaryMeters > 0 ? boundaryMeters : DEFAULT_BOUNDARY_METERS;
        maxCallsPerRun = maxCallsPerRun > 0 ? maxCallsPerRun : DEFAULT_MAX_CALLS_PER_RUN;
    }
}
