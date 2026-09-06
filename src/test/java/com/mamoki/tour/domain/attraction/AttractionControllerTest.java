package com.mamoki.tour.domain.attraction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.mamoki.tour.global.enums.DataStatus;
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
                        LocalDateTime.of(2025, 9, 4, 14, 15, 26))),
                676, 1, 20, DataStatus.AVAILABLE, LocalDateTime.of(2026, 9, 6, 12, 0), "KorService2"));

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
                        "12", null, null, null, null)),
                1, 1, 20, DataStatus.AVAILABLE, LocalDateTime.now(), "KorService2"));

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
                .willReturn(AttractionListResponse.noData(1, 20, "KorService2"));

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
}
