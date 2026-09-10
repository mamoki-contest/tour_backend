package com.mamoki.tour.domain.region.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mamoki.tour.domain.region.dto.RegionVisitScaleResponse;
import com.mamoki.tour.domain.region.service.RegionVisitScaleService;
import com.mamoki.tour.global.rsdata.ResultCodes;
import com.mamoki.tour.global.rsdata.RsData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "지역", description = "강원 시·군 단위 탐색")
@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {

    private final RegionVisitScaleService regionVisitScaleService;

    public RegionController(RegionVisitScaleService regionVisitScaleService) {
        this.regionVisitScaleService = regionVisitScaleService;
    }

    /**
     * 지도에 표시할 시·군 방문 규모를 조회한다.
     *
     * <p>데이터를 얻지 못해도 200 으로 응답하며, 그 사실은 dataStatus 로 알린다.
     */
    @Operation(
            summary = "지역 방문 규모 조회",
            description = """
                    강원 18개 시·군의 방문 규모를 같은 기준 기간으로 반환합니다.
                    지도에서 시·군을 같은 색 범위로 칠하는 데 사용합니다.

                    ### 무엇을 센 값인가
                    한국관광공사 빅데이터 지역별 방문자수의 **외지인·외국인 합계**입니다.
                    현지인은 거주 인구가 그대로 잡히는 값이라 제외했습니다. 실제 관광객 수가
                    아니라 통신 기반 추정치이며, 관광지 방문객 수와도 다릅니다.

                    ### 상대 구간 (`level`)
                    `VERY_HIGH` ~ `VERY_LOW` 는 **조회한 시·군 사이의 상대 순서**입니다.
                    절대 등급이 아니므로 다른 기간이나 다른 지역 집합의 구간과 비교하지 마세요.
                    방문자 수가 같으면 같은 구간·같은 순위를 받습니다.

                    ### 기준 기간
                    공급자가 최근 데이터를 바로 공개하지 않아 `periodStart` ~ `periodEnd` 는
                    오늘과 한 달 가까이 떨어져 있습니다. 요일 편차를 줄이려고 한 주를 묶습니다.
                    모든 시·군이 같은 기간을 쓰므로 시·군끼리는 그대로 비교할 수 있습니다.

                    ### 결측
                    값을 얻지 못한 시·군도 목록에 담기며 `dataStatus` 가 `NO_DATA` 이고
                    `visitorCount` 와 `level` 이 null 입니다. 지도에서 최하 구간으로 칠하지 말고
                    정보 없음으로 구분해 표시하세요.

                    읍·면·동 단위는 제공하지 않습니다. 공급자가 시·군구까지만 집계합니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. dataStatus 로 데이터 상태를 확인합니다.")
    })
    @GetMapping("/visit-scale")
    public RsData<RegionVisitScaleResponse> getVisitScale() {
        return RsData.of(ResultCodes.OK, "지역 방문 규모를 조회했습니다.",
                regionVisitScaleService.getVisitScale());
    }
}
