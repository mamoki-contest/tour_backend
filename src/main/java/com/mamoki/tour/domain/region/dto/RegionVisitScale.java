package com.mamoki.tour.domain.region.dto;

import com.mamoki.tour.domain.region.enums.RegionVisitLevel;
import com.mamoki.tour.global.enums.DataStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지도에 표시할 시·군 하나의 방문 규모.
 *
 * <p>값을 얻지 못한 시·군은 목록에서 빼지 않고 {@code NO_DATA} 로 남긴다. 지도에서 빠진
 * 시·군은 방문객이 없는 곳처럼 보이기 때문이다.
 */
@Schema(description = "시·군 방문 규모. 결측은 낮은 값이 아니라 정보 없음으로 표시합니다.")
public record RegionVisitScale(

        @Schema(description = "법정동 시·군구 코드 5자리", example = "51150")
        String lawdCode,

        @Schema(description = "한국관광공사 시·군구 코드. 확인되지 않았으면 null", example = "1")
        String sigunguCode,

        @Schema(description = "시·군명", example = "강릉시")
        String name,

        @Schema(description = """
                기준 기간의 외지인·외국인 합계. 현지인은 제외합니다.
                정보 없음이면 null 이며, 0 으로 채우지 않습니다.""", example = "1234567")
        Long visitorCount,

        @Schema(description = """
                같은 기준 기간 안에서 시·군끼리 비교한 상대 구간.
                절대 등급이 아니며 다른 기간의 구간과 비교하지 마세요. 정보 없음이면 null""")
        RegionVisitLevel level,

        @Schema(description = "방문 규모 순위. 값이 같으면 같은 순위입니다. 정보 없음이면 null", example = "1")
        Integer rank,

        @Schema(description = "집계에 들어간 날 수. 기준 기간보다 적으면 부분 집계입니다", example = "7")
        Integer dayCount,

        @Schema(description = "AVAILABLE=집계 있음, NO_DATA=정보 없음")
        DataStatus dataStatus
) {

    public static RegionVisitScale noData(String lawdCode, String sigunguCode, String name) {
        return new RegionVisitScale(lawdCode, sigunguCode, name, null, null, null, null,
                DataStatus.NO_DATA);
    }
}
