package com.mamoki.tour.domain.visittiming.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.mamoki.tour.domain.visittiming.enums.DateMode;
import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;
import com.mamoki.tour.global.enums.DataStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관광지 목록 항목에 붙는 날짜 탐색 결과.
 *
 * <p>집중률 원본 예측값은 담지 않는다. 공급자가 공식 등급 기준을 주지 않아 절대 수준으로
 * 읽을 수 없고, 값을 노출하면 서로 다른 관광지를 그 값으로 줄 세우게 되기 때문이다.
 * 여기서 제공하는 것은 <b>같은 장소의 30일 분포 안에서의 상대 수준</b>뿐이다.
 */
@Schema(description = """
        날짜 탐색 결과. status 의 LOW/NORMAL/HIGH 는 그 장소 자신의 향후 30일 분포 안에서의
        상대 수준입니다. 서로 다른 관광지의 status 를 모아 혼잡도 순위로 쓰면 안 됩니다.""")
public record VisitTiming(

        @Schema(description = "요청한 날짜 모드", example = "FIXED")
        DateMode dateMode,

        @Schema(description = """
                판정 결과.
                LOW=이 장소 기준 한산, NORMAL=보통, HIGH=혼잡,
                NO_DATA=예측 없음 또는 판정할 만큼 모이지 않음, OUT_OF_RANGE=지원 범위 밖 날짜""")
        VisitTimingStatus status,

        @Schema(description = "확정 모드에서 평가한 선택일. 유연 모드면 null", example = "2026-09-20")
        LocalDate selectedDate,

        @Schema(description = "유연 모드에서 고른 한산 예상일. 확정 모드이거나 고를 수 없으면 null",
                example = "2026-09-15")
        LocalDate quietestDate,

        @Schema(description = "판정에 쓴 유효 예측일 수. 0 이면 이 장소의 예측을 얻지 못한 것입니다.",
                example = "30")
        int forecastDays,

        @Schema(description = "지원 범위 시작일(오늘)", example = "2026-09-07")
        LocalDate supportedFrom,

        @Schema(description = "지원 범위 종료일(오늘부터 30일째)", example = "2026-10-06")
        LocalDate supportedTo,

        @Schema(description = "예측 데이터의 신선도. AVAILABLE / STALE / NO_DATA")
        DataStatus dataStatus,

        @Schema(description = "예측 데이터를 수집한 시각. 정보 없음이면 null")
        LocalDateTime collectedAt,

        @Schema(description = "데이터 출처", example = "TatsCnctrRateService")
        String source
) {

    public static VisitTiming of(DateMode dateMode, VisitTimingVerdict verdict,
                                 LocalDate supportedFrom, LocalDate supportedTo,
                                 DataStatus dataStatus, LocalDateTime collectedAt, String source) {

        return new VisitTiming(
                dateMode,
                verdict.status(),
                verdict.selectedDate(),
                verdict.quietestDate(),
                verdict.forecastDays(),
                supportedFrom,
                supportedTo,
                dataStatus,
                collectedAt,
                source);
    }
}
