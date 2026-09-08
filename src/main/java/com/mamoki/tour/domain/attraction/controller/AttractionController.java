package com.mamoki.tour.domain.attraction.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mamoki.tour.domain.attraction.dto.AttractionDetailResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionListResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSearchRequest;
import com.mamoki.tour.domain.attraction.service.AttractionDetailService;
import com.mamoki.tour.domain.attraction.service.AttractionService;
import com.mamoki.tour.global.rsdata.ResultCodes;
import com.mamoki.tour.global.rsdata.RsData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "관광지", description = "강원 관광지 탐색")
@RestController
@RequestMapping("/api/v1/attractions")
public class AttractionController {

    private final AttractionService attractionService;
    private final AttractionDetailService attractionDetailService;

    public AttractionController(AttractionService attractionService,
                                AttractionDetailService attractionDetailService) {
        this.attractionService = attractionService;
        this.attractionDetailService = attractionDetailService;
    }

    /**
     * 탐색 홈의 관광지 목록을 조회한다.
     *
     * <p>데이터를 얻지 못해도 200 으로 응답하며, 그 사실은 dataStatus 로 알린다.
     * 공급자 장애는 오류가 아니라 정상 응답의 한 상태다.
     *
     * <p>dateMode 를 주면 항목마다 날짜 탐색 결과(visitTiming)를 함께 내려준다.
     * 지원 범위 밖 날짜는 400 이 아니라 200 + OUT_OF_RANGE 로 안내한다.
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

                    온라인 언급량, TMAP 검색순위, 입장객 수, 날짜 탐색은 각각 독립 필드입니다.
                    범위와 기준 시점이 달라 하나의 점수로 합치지 않습니다.

                    ### 날짜 탐색
                    `dateMode` 를 주면 항목마다 `visitTiming` 이 함께 내려옵니다. 비우면 목록만 반환합니다.

                    - `FIXED` + `visitDate`: 그 날이 **그 장소 자신의 향후 30일 분포** 안에서
                      한산(`LOW`)/보통(`NORMAL`)/혼잡(`HIGH`) 중 어디인지 반환합니다.
                    - `FLEXIBLE`: 그 장소의 향후 30일 중 한산 예상일(`quietestDate`)을 반환합니다.
                      `visitDate` 는 보내지 않습니다.

                    `visitTiming.status` 는 장소 내부의 상대 수준입니다.
                    **서로 다른 관광지의 status 를 모아 혼잡도 순위로 쓰지 마세요.**
                    그래서 원본 예측값도, 예측값 기준 정렬 파라미터도 제공하지 않습니다.

                    예측이 없거나 판정할 만큼 모이지 않으면 `NO_DATA`,
                    지원 범위(`supportedFrom` ~ `supportedTo`) 밖 미래 날짜는 400 이 아니라
                    200 + `OUT_OF_RANGE` 로 안내합니다. 과거 날짜는 400 입니다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. dataStatus 로 데이터 상태를 확인합니다."),
            @ApiResponse(responseCode = "400", description = """
                    조회 조건이 올바르지 않음. data 에 필드별 오류가 담깁니다.
                    과거 날짜, FIXED 인데 visitDate 누락, FLEXIBLE 인데 visitDate 동봉이 여기에 해당합니다.""")
    })
    @GetMapping
    public RsData<AttractionListResponse> getAttractions(
            @Valid @ParameterObject @ModelAttribute AttractionSearchRequest request) {

        return RsData.of(ResultCodes.OK, "관광지 목록을 조회했습니다.", attractionService.search(request));
    }

    /**
     * 관광지 상세를 조회한다.
     *
     * <p>기본정보, 30일 예측, 대체지 후보, 함께 가기 좋은 곳을 각각 독립 필드로 돌려준다.
     * 기본정보를 얻지 못하면 무엇에 대한 상세인지 말할 수 없으므로 404 로 응답한다.
     * 예측이나 연관 장소가 비어 있는 것은 오류가 아니라 정상 응답의 한 상태다.
     */
    @Operation(
            summary = "관광지 상세 조회",
            description = """
                    관광지 상세 정보를 반환합니다. 기본정보·30일 방문 혼잡도 예측·대체지 후보·
                    함께 가기 좋은 곳이 각각 독립 필드입니다. 범위와 기준 시점이 달라 하나의 점수로
                    합치지 않으며, 어느 하나의 결측을 다른 신호로 추정하지 않습니다.

                    ### 30일 방문 혼잡도 예측
                    `visitTiming` 은 이 장소의 한산 예상일과 지원 범위를, `dailyForecast` 는
                    지원 범위 30일의 하루치 판정을 담습니다. 두 값 모두 **그 장소 자신의 30일 분포
                    안에서의 상대 수준**입니다. 집중률 원본값은 담지 않습니다.
                    값을 노출하면 서로 다른 관광지를 그 값으로 줄 세울 수 있게 되기 때문입니다.

                    ### 대체지 후보 (`alternatives`)
                    원래 장소를 **대신할** 곳입니다. 다음을 모두 만족한 관광지만 담깁니다.

                    - 연관 장소 중 대분류가 관광지인 곳
                    - 원래 장소와 다른 곳 (정규화한 이름으로 판단)
                    - 유효한 방문 혼잡도 예측을 가진 곳

                    예측이 없는 곳은 연관 순위가 높아도 담기지 않습니다. 한산하다는 근거 없이
                    사람을 보내지 않기 위한 자격이며, 순위나 큐레이션이 이를 우회하지 못합니다.

                    ### 함께 가기 좋은 곳 (`companions`)
                    같은 여행에서 **함께 갈** 음식점·숙박시설입니다. 대체지가 아니므로
                    `eligibleAsAlternative` 는 항상 false 입니다.

                    ### 빈 목록의 해석
                    두 묶음 모두 `status` 로 비어 있는 이유를 구분합니다.
                    `NO_RELATED_DATA` 는 공급자 데이터를 얻지 못했거나 이 관광지가 연관 목록에
                    없는 것이고, `NONE_QUALIFIED` 는 연관 장소는 받았지만 자격을 충족한 곳이
                    없는 것입니다. 둘을 같은 문구로 표시하지 마세요.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = """
                    조회 성공. dataStatus 와 각 묶음의 status 로 데이터 상태를 확인합니다."""),
            @ApiResponse(responseCode = "404", description = "기본정보를 얻지 못해 상세를 구성할 수 없음")
    })
    @GetMapping("/{contentId}")
    public RsData<AttractionDetailResponse> getAttraction(
            @Parameter(description = "표준 관광지 식별자", example = "126508")
            @PathVariable String contentId) {

        return RsData.of(ResultCodes.OK, "관광지 상세를 조회했습니다.",
                attractionDetailService.getDetail(contentId));
    }
}
