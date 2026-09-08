package com.mamoki.tour.domain.attraction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import com.mamoki.tour.domain.attraction.controller.AttractionController;
import com.mamoki.tour.domain.attraction.dto.AttractionListResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.attraction.service.AttractionService;
import com.mamoki.tour.domain.attraction.dto.OnlineMentionView;
import com.mamoki.tour.domain.attraction.dto.TmapRankView;
import com.mamoki.tour.domain.attraction.dto.VisitorStatsView;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;
import com.mamoki.tour.domain.visittiming.enums.DateMode;
import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.enums.MentionStatus;
import com.mamoki.tour.global.exception.GlobalExceptionHandler;

class AttractionControllerTest {

    private MockMvc mvc;
    private AttractionService attractionService;

    @BeforeEach
    void setUp() {
        attractionService = Mockito.mock(AttractionService.class);

        mvc = MockMvcBuilders.standaloneSetup(new AttractionController(attractionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @Test
    @DisplayName("관광지 목록을 RsData 봉투로 반환한다")
    void returnsListInRsData() throws Exception {
        given(attractionService.search(any())).willReturn(new AttractionListResponse(
                List.of(new AttractionResponse("2868839", "가람집옹심이", null,
                        "강원특별자치도 강릉시 공항길30번길 16",
                        new BigDecimal("37.7611934162"), new BigDecimal("128.9393320379"),
                        "39", "51150", "강릉시", null,
                        LocalDateTime.of(2025, 9, 4, 14, 15, 26),
                        new OnlineMentionView(MentionStatus.COLLECTED, 140006L,
                                LocalDateTime.of(2026, 9, 6, 12, 0), "name+sigungu"),
                        TmapRankView.notAvailable(),
                        VisitorStatsView.notImported(),
                        null)),
                676, 1, 20, null, DataStatus.AVAILABLE,
                LocalDateTime.of(2026, 9, 6, 12, 0), "KorService2"));

        mvc.perform(get("/api/v1/attractions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(676))
                .andExpect(jsonPath("$.data.dataStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.source").value("KorService2"))
                .andExpect(jsonPath("$.data.items[0].contentId").value("2868839"))
                .andExpect(jsonPath("$.data.items[0].regionName").value("강릉시"))
                // 판별용 메서드가 응답 계약에 새어 나오지 않아야 한다.
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.fail").doesNotExist());
    }

    @Test
    @DisplayName("결측 값은 응답에서도 null 로 유지된다")
    void keepsMissingValuesNull() throws Exception {
        given(attractionService.search(any())).willReturn(new AttractionListResponse(
                List.of(new AttractionResponse("1", "좌표 없는 장소", null, null, null, null,
                        "12", null, null, null, null,
                        OnlineMentionView.notCollected(null),
                        TmapRankView.notAvailable(),
                        VisitorStatsView.notImported(),
                        null)),
                1, 1, 20, null, DataStatus.AVAILABLE, LocalDateTime.now(), "KorService2"));

        mvc.perform(get("/api/v1/attractions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].latitude").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].centerRank").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].regionName").doesNotExist());
    }

    @Test
    @DisplayName("공급자 데이터가 없어도 200 으로 응답하고 상태로 알린다")
    void returnsOkWithNoDataStatus() throws Exception {
        given(attractionService.search(any()))
                .willReturn(AttractionListResponse.noData(1, 20, "KorService2", null));

        mvc.perform(get("/api/v1/attractions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.dataStatus").value("NO_DATA"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.collectedAt").doesNotExist());
    }

    @Test
    @DisplayName("잘못된 조회 조건은 400 과 필드별 오류로 응답한다")
    void rejectsInvalidCondition() throws Exception {
        mvc.perform(get("/api/v1/attractions").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.data[0].field").value("size"));
    }

    @Test
    @DisplayName("시·군구 코드 형식이 어긋나면 400 으로 응답한다")
    void rejectsMalformedSigunguCode() throws Exception {
        mvc.perform(get("/api/v1/attractions").param("sigunguCode", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.data[0].field").value("sigunguCode"));
    }

    @Test
    @DisplayName("확정 모드 응답의 날짜 탐색 필드가 모두 내려간다")
    void serializesFixedVisitTiming() throws Exception {
        LocalDate today = LocalDate.of(2026, 9, 8);
        given(attractionService.search(any())).willReturn(listOf(new VisitTiming(
                DateMode.FIXED, VisitTimingStatus.LOW, today.plusDays(3), null, 30,
                today, today.plusDays(29), DataStatus.AVAILABLE,
                LocalDateTime.of(2026, 9, 8, 3, 0), "TatsCnctrRateService")));

        mvc.perform(get("/api/v1/attractions")
                        .param("dateMode", "FIXED")
                        .param("visitDate", "2026-09-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].visitTiming.dateMode").value("FIXED"))
                .andExpect(jsonPath("$.data.items[0].visitTiming.status").value("LOW"))
                .andExpect(jsonPath("$.data.items[0].visitTiming.selectedDate").value("2026-09-11"))
                .andExpect(jsonPath("$.data.items[0].visitTiming.quietestDate").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].visitTiming.forecastDays").value(30))
                .andExpect(jsonPath("$.data.items[0].visitTiming.supportedFrom").value("2026-09-08"))
                .andExpect(jsonPath("$.data.items[0].visitTiming.supportedTo").value("2026-10-07"))
                .andExpect(jsonPath("$.data.items[0].visitTiming.dataStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.items[0].visitTiming.source").value("TatsCnctrRateService"))
                // 집중률 원본값은 계약에 없다. 있으면 장소 간 절대 순위를 만들 수 있게 된다.
                .andExpect(jsonPath("$.data.items[0].visitTiming.rate").doesNotExist());
    }

    @Test
    @DisplayName("유연 모드 응답에는 선택일 대신 한산 예상일이 담긴다")
    void serializesFlexibleVisitTiming() throws Exception {
        LocalDate today = LocalDate.of(2026, 9, 8);
        given(attractionService.search(any())).willReturn(listOf(new VisitTiming(
                DateMode.FLEXIBLE, VisitTimingStatus.LOW, null, today.plusDays(7), 30,
                today, today.plusDays(29), DataStatus.AVAILABLE, LocalDateTime.now(),
                "TatsCnctrRateService")));

        mvc.perform(get("/api/v1/attractions").param("dateMode", "FLEXIBLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].visitTiming.dateMode").value("FLEXIBLE"))
                .andExpect(jsonPath("$.data.items[0].visitTiming.quietestDate").value("2026-09-15"))
                .andExpect(jsonPath("$.data.items[0].visitTiming.selectedDate").doesNotExist());
    }

    @Test
    @DisplayName("지원 범위 밖 날짜는 200 으로 범위를 안내한다")
    void serializesOutOfRange() throws Exception {
        LocalDate today = LocalDate.of(2026, 9, 8);
        given(attractionService.search(any())).willReturn(listOf(new VisitTiming(
                DateMode.FIXED, VisitTimingStatus.OUT_OF_RANGE, today.plusDays(60), null, 0,
                today, today.plusDays(29), DataStatus.AVAILABLE, LocalDateTime.now(),
                "TatsCnctrRateService")));

        mvc.perform(get("/api/v1/attractions")
                        .param("dateMode", "FIXED")
                        .param("visitDate", "2026-11-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].visitTiming.status").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.data.items[0].visitTiming.supportedTo").value("2026-10-07"));
    }

    @Test
    @DisplayName("날짜 모드를 지정하지 않으면 날짜 탐색 결과가 붙지 않는다")
    void omitsVisitTimingWithoutDateMode() throws Exception {
        given(attractionService.search(any())).willReturn(listOf(null));

        mvc.perform(get("/api/v1/attractions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].contentId").value("1"))
                .andExpect(jsonPath("$.data.items[0].visitTiming").doesNotExist());
    }

    @Test
    @DisplayName("확정 모드에 선택일이 없으면 400 으로 응답한다")
    void rejectsFixedWithoutVisitDate() throws Exception {
        mvc.perform(get("/api/v1/attractions").param("dateMode", "FIXED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.data[0].field").value("visitDateRequiredWhenFixed"));
    }

    @Test
    @DisplayName("유연 모드에 선택일을 보내면 400 으로 응답한다")
    void rejectsFlexibleWithVisitDate() throws Exception {
        mvc.perform(get("/api/v1/attractions")
                        .param("dateMode", "FLEXIBLE")
                        .param("visitDate", "2026-09-11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.data[0].field").value("visitDateAbsentWhenFlexible"));
    }

    @Test
    @DisplayName("알 수 없는 날짜 모드는 내부 클래스명을 드러내지 않고 400 으로 응답한다")
    void rejectsUnknownDateModeWithoutLeakingType() throws Exception {
        mvc.perform(get("/api/v1/attractions").param("dateMode", "SOMEDAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.data[0].field").value("dateMode"))
                // 스프링 기본 메시지에는 DateMode 의 전체 클래스명이 들어간다. 그대로 내보내지 않는다.
                .andExpect(jsonPath("$.data[0].msg").value("형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data[0].msg").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("com.mamoki"))));
    }

    /** 날짜 탐색 결과만 바꿔 가며 쓰는 한 건짜리 목록 응답. */
    private AttractionListResponse listOf(VisitTiming visitTiming) {
        return new AttractionListResponse(
                List.of(new AttractionResponse("1", "경포대", null, null, null, null,
                        "12", "51150", "강릉시", null, null,
                        OnlineMentionView.notCollected(null),
                        TmapRankView.notAvailable(),
                        VisitorStatsView.notImported(),
                        visitTiming)),
                1, 1, 20, null, DataStatus.AVAILABLE, LocalDateTime.now(), "KorService2");
    }
}
