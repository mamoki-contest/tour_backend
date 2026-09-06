package com.mamoki.tour.domain.attraction.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mamoki.tour.domain.attraction.dto.AttractionListResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSearchRequest;
import com.mamoki.tour.domain.attraction.service.AttractionService;
import com.mamoki.tour.global.rsdata.ResultCodes;
import com.mamoki.tour.global.rsdata.RsData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "관광지", description = "강원 관광지 탐색")
@RestController
@RequestMapping("/api/v1/attractions")
public class AttractionController {

    private final AttractionService attractionService;

    public AttractionController(AttractionService attractionService) {
        this.attractionService = attractionService;
    }

    /**
     * 탐색 홈의 관광지 목록을 조회한다.
     *
     * <p>데이터를 얻지 못해도 200 으로 응답하며, 그 사실은 dataStatus 로 알린다.
     * 공급자 장애는 오류가 아니라 정상 응답의 한 상태다.
     */
    @Operation(
            summary = "관광지 목록 조회",
            description = """
                    탐색 홈에 표시할 강원 관광지 목록을 반환합니다.

                    공급자 응답은 24시간 캐시하며, 갱신에 실패하면 최종 정상 데이터로 응답합니다.
                    데이터를 얻지 못한 경우에도 200 으로 응답하고 `dataStatus` 를 `NO_DATA` 로 내려줍니다.
                    빈 목록과 정보 없음을 구분해 표시하세요.

                    `sort` 를 지정하면 조회 범위 전체를 대상으로 온라인 언급량 순으로 정렬합니다.
                    온라인 언급량이 산정되지 않았거나 이름이 모호한 장소는 정렬 대상에서 빠져
                    목록 뒤쪽에 모입니다. `온라인 언급 적은 순` 의 상단으로 올리지 않습니다.

                    온라인 언급량, TMAP 검색순위, 입장객 수는 각각 독립 필드입니다.
                    범위와 기준 시점이 달라 하나의 점수로 합치지 않습니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. dataStatus 로 데이터 상태를 확인합니다."),
            @ApiResponse(responseCode = "400", description = "조회 조건이 올바르지 않음. data 에 필드별 오류가 담깁니다.")
    })
    @GetMapping
    public RsData<AttractionListResponse> getAttractions(
            @Valid @ParameterObject @ModelAttribute AttractionSearchRequest request) {

        return RsData.of(ResultCodes.OK, "관광지 목록을 조회했습니다.", attractionService.search(request));
    }
}
