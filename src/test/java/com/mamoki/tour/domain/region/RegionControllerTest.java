package com.mamoki.tour.domain.region;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.mamoki.tour.domain.region.controller.RegionController;
import com.mamoki.tour.domain.region.dto.RegionVisitScale;
import com.mamoki.tour.domain.region.dto.RegionVisitScaleResponse;
import com.mamoki.tour.domain.region.enums.RegionVisitLevel;
import com.mamoki.tour.domain.region.service.RegionVisitScaleService;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.exception.GlobalExceptionHandler;
import com.mamoki.tour.global.exception.ServiceException;
import com.mamoki.tour.global.rsdata.ResultCodes;

/**
 * 지역 방문 규모 조회의 응답 계약.
 *
 * <p>이 엔드포인트는 지도를 칠하는 데 쓰인다. 값을 얻지 못한 시·군이 목록에서 빠지면 지도에
 * 구멍이 나거나 최하 구간으로 칠해져 "방문객이 없는 곳"처럼 보인다. 그래서 결측도 목록에
 * 남고 상태로 구분된다는 점을 응답 수준에서 못 박는다.
 */
class RegionControllerTest {

    private MockMvc mvc;
    private RegionVisitScaleService regionVisitScaleService;

    @BeforeEach
    void setUp() {
        regionVisitScaleService = Mockito.mock(RegionVisitScaleService.class);

        mvc = MockMvcBuilders.standaloneSetup(new RegionController(regionVisitScaleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @Test
    @DisplayName("시·군 방문 규모를 RsData 봉투로 반환한다")
    void returnsVisitScaleInRsData() throws Exception {
        given(regionVisitScaleService.getVisitScale()).willReturn(response(
                List.of(new RegionVisitScale("51150", "1", "강릉시", 1_234_567L,
                        RegionVisitLevel.VERY_HIGH, 1, 7, DataStatus.AVAILABLE)),
                DataStatus.AVAILABLE, 1));

        mvc.perform(get("/api/v1/regions/visit-scale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.items[0].lawdCode").value("51150"))
                .andExpect(jsonPath("$.data.items[0].name").value("강릉시"))
                .andExpect(jsonPath("$.data.items[0].visitorCount").value(1_234_567L))
                .andExpect(jsonPath("$.data.items[0].level").value("VERY_HIGH"))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.periodStart").value("2026-08-05"))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-08-11"))
                .andExpect(jsonPath("$.data.source").value("DataLabService"))
                // 판별용 메서드가 응답 계약에 새어 나오지 않아야 한다.
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    @DisplayName("값을 얻지 못한 시·군도 목록에 남고 방문자 수는 null 이다")
    void keepsRegionsWithoutData() throws Exception {
        given(regionVisitScaleService.getVisitScale()).willReturn(response(
                List.of(new RegionVisitScale("51150", "1", "강릉시", 1_234_567L,
                                RegionVisitLevel.VERY_HIGH, 1, 7, DataStatus.AVAILABLE),
                        RegionVisitScale.noData("51830", "17", "양양군")),
                DataStatus.AVAILABLE, 1));

        mvc.perform(get("/api/v1/regions/visit-scale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[1].name").value("양양군"))
                .andExpect(jsonPath("$.data.items[1].dataStatus").value("NO_DATA"))
                // 0 으로 채우면 방문객이 없는 곳이 된다. null 이어야 한다.
                .andExpect(jsonPath("$.data.items[1].visitorCount").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].level").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].rank").doesNotExist());
    }

    @Test
    @DisplayName("최종 정상 데이터로 응답할 때도 200 이고 상태로 알린다")
    void reportsStaleWithOk() throws Exception {
        given(regionVisitScaleService.getVisitScale()).willReturn(response(
                List.of(new RegionVisitScale("51150", "1", "강릉시", 1_234_567L,
                        RegionVisitLevel.VERY_HIGH, 1, 7, DataStatus.AVAILABLE)),
                DataStatus.STALE, 1));

        mvc.perform(get("/api/v1/regions/visit-scale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataStatus").value("STALE"))
                .andExpect(jsonPath("$.data.collectedAt").exists());
    }

    @Test
    @DisplayName("한 시·군도 값을 얻지 못해도 200 으로 응답한다")
    void reportsNoDataWithOk() throws Exception {
        given(regionVisitScaleService.getVisitScale()).willReturn(new RegionVisitScaleResponse(
                List.of(RegionVisitScale.noData("51150", "1", "강릉시")),
                null, null, 18, 0, DataStatus.NO_DATA, null, "DataLabService"));

        mvc.perform(get("/api/v1/regions/visit-scale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataStatus").value("NO_DATA"))
                .andExpect(jsonPath("$.data.availableRegions").value(0))
                .andExpect(jsonPath("$.data.totalRegions").value(18))
                .andExpect(jsonPath("$.data.collectedAt").doesNotExist())
                .andExpect(jsonPath("$.data.periodStart").doesNotExist());
    }

    @Test
    @DisplayName("서비스가 처리할 수 없다고 하면 그 코드로 응답한다")
    void mapsServiceException() throws Exception {
        given(regionVisitScaleService.getVisitScale())
                .willThrow(new ServiceException(ResultCodes.INVALID_REQUEST, "조회할 수 없습니다."));

        mvc.perform(get("/api/v1/regions/visit-scale"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.msg").value("조회할 수 없습니다."));
    }

    private static RegionVisitScaleResponse response(List<RegionVisitScale> items,
                                                     DataStatus dataStatus, int availableRegions) {
        return new RegionVisitScaleResponse(items,
                LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 11),
                18, availableRegions, dataStatus,
                LocalDateTime.of(2026, 9, 10, 3, 0), "DataLabService");
    }
}
