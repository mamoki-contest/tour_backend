package com.mamoki.tour.domain.currentaccess.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mamoki.tour.domain.currentaccess.enums.ParkingCongestion;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관광지 주변 주차장 한 곳.
 *
 * <p>실시간 잔여면을 아는 주차장과 표준데이터에만 있는 주차장이 한 목록에 섞여 있다.
 * 실시간이 없는 곳은 {@code occupiedLots}·{@code availableLots}·{@code congestion} 이
 * null 이다. <b>null 을 0 으로 읽으면 만차로 보인다.</b>
 *
 * @param totalLots      전체 주차면. 실시간 공급자의 값이거나 표준데이터의 주차구획수다
 * @param occupiedLots   점유 대수. 공급자 원본값을 그대로 담는다
 * @param availableLots  잔여면 = 전체 − 점유. 우리가 계산한 값이다
 * @param observedAt     우리가 그 값을 받은 시각. <b>공급자 기준시각이 아니다</b> —
 *                       강릉시 응답에 기준시각 필드가 없고 Last-Modified 도 오지 않는다
 */
@Schema(description = """
        관광지 주변 주차장 한 곳. 실시간 값이 없는 주차장은 점유·잔여·혼잡이 null 입니다.
        null 을 0 으로 해석하지 마세요.""")
public record ParkingLotView(

        @Schema(description = "주차장 이름", example = "강릉역")
        String name,

        @Schema(description = "위도")
        BigDecimal latitude,

        @Schema(description = "경도")
        BigDecimal longitude,

        @Schema(description = "관광지 좌표로부터의 직선 거리(m)", example = "420")
        int distanceMeters,

        @Schema(description = "전체 주차면. 알 수 없으면 null", example = "410")
        Integer totalLots,

        @Schema(description = "점유 대수. 실시간 정보가 없으면 null", example = "98")
        Integer occupiedLots,

        @Schema(description = "잔여면(전체 − 점유). 실시간 정보가 없으면 null", example = "312")
        Integer availableLots,

        @Schema(description = """
                PLENTY=여유(잔여 30% 이상), MODERATE=보통(10% 이상),
                CROWDED=혼잡(10% 미만), FULL=만차(잔여 0). 실시간 정보가 없으면 null""")
        ParkingCongestion congestion,

        @Schema(description = "실시간 값을 받은 시각. 공급자 기준시각이 아니라 우리 수신 시각입니다")
        LocalDateTime observedAt,

        @Schema(description = "이 주차장 정보의 출처", example = "강릉시 교통정보 조회서비스")
        String source
) {

    /** 지금 자리가 몇 개인지 말할 수 있는 항목인지. */
    public boolean hasRealtime() {
        return availableLots != null;
    }
}
