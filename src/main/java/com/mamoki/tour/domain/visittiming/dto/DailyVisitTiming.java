package com.mamoki.tour.domain.visittiming.dto;

import java.time.LocalDate;

import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 상세 응답에 담는 하루치 판정.
 *
 * <p>{@link DailyConcentration} 과 달리 공급자 원본값을 담지 않는다. 30일을 한 번에 내려주면
 * 값이 있을 때 장소 사이의 절대 비교가 쉬워지므로, 여기서도 <b>그 장소 자신의 분포 안에서의
 * 상대 수준</b>만 내려준다.
 *
 * @param status 이 날의 상대 수준. 예측이 없거나 판정할 만큼 모이지 않았으면 {@code NO_DATA}.
 */
@Schema(description = """
        하루치 판정. status 는 이 장소 자신의 30일 분포 안에서의 상대 수준입니다.
        다른 관광지의 같은 날 status 와 비교해 혼잡도 순위로 쓰면 안 됩니다.""")
public record DailyVisitTiming(

        @Schema(description = "예측 날짜", example = "2026-09-11")
        LocalDate date,

        @Schema(description = "LOW=이 장소 기준 한산, NORMAL=보통, HIGH=혼잡, NO_DATA=예측 없음")
        VisitTimingStatus status
) {
}
