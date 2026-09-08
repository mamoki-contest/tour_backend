package com.mamoki.tour.domain.visittiming.dto;

import java.time.LocalDate;

import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;

/**
 * 해석 규칙이 내린 판정. 공급자 신선도와 무관한 순수 계산 결과다.
 *
 * @param selectedDate  날짜 확정 모드에서 평가한 날짜. 유연 모드면 null.
 * @param quietestDate  유연 모드에서 고른 한산 예상일. 확정 모드이거나 고를 수 없으면 null.
 * @param forecastDays  판정에 쓴 유효 예측일 수. 임의로 채운 날은 포함하지 않는다.
 */
public record VisitTimingVerdict(
        VisitTimingStatus status,
        LocalDate selectedDate,
        LocalDate quietestDate,
        int forecastDays
) {

    public static VisitTimingVerdict noData(LocalDate selectedDate, int forecastDays) {
        return new VisitTimingVerdict(VisitTimingStatus.NO_DATA, selectedDate, null, forecastDays);
    }

    public static VisitTimingVerdict outOfRange(LocalDate selectedDate, int forecastDays) {
        return new VisitTimingVerdict(VisitTimingStatus.OUT_OF_RANGE, selectedDate, null, forecastDays);
    }
}
