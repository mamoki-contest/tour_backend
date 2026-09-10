package com.mamoki.tour.domain.region.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.mamoki.tour.global.enums.DataStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지역 방문 규모 지도 응답.
 *
 * <p>모든 시·군이 같은 기준 기간을 쓴다. 시·군마다 기간이 다르면 상대 구간을 비교할 수 없다.
 *
 * @param periodStart 기준 기간 시작일. 공급자 공개 지연 때문에 오늘과 한참 떨어져 있다.
 * @param dataStatus  전체 상태. 한 시·군이라도 값을 얻었으면 AVAILABLE 또는 STALE 이다.
 */
@Schema(description = "강원 시·군 방문 규모. 모든 시·군이 같은 기준 기간을 사용합니다.")
public record RegionVisitScaleResponse(

        @Schema(description = "시·군별 방문 규모. 값이 없는 시·군도 NO_DATA 로 담깁니다")
        List<RegionVisitScale> items,

        @Schema(description = "기준 기간 시작일", example = "2026-08-05")
        LocalDate periodStart,

        @Schema(description = "기준 기간 종료일", example = "2026-08-11")
        LocalDate periodEnd,

        @Schema(description = "조회 대상 시·군 수", example = "18")
        int totalRegions,

        @Schema(description = "값을 얻은 시·군 수", example = "18")
        int availableRegions,

        @Schema(description = """
                데이터 상태.
                AVAILABLE=유효, STALE=갱신 실패로 최종 정상 데이터 사용, NO_DATA=정보 없음""")
        DataStatus dataStatus,

        @Schema(description = "응답에 사용한 데이터의 수집 시각. NO_DATA 이면 null")
        LocalDateTime collectedAt,

        @Schema(description = "데이터 출처")
        String source
) {
}
