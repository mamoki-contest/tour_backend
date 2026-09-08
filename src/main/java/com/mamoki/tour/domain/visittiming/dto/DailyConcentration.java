package com.mamoki.tour.domain.visittiming.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 어떤 장소의 하루치 집중률 예측.
 *
 * <p>{@code rate} 는 공급자 원본값이며 <b>응답에 그대로 노출하지 않는다.</b>
 * 공급자가 공식 등급 기준을 주지 않아 절대 수준으로 읽을 수 없고, 장소 사이의
 * 비교에 쓰이면 PRD 가 금지한 절대 혼잡도 순위가 되기 때문이다.
 *
 * @param rate 집중률 예측값. 공급자가 값을 주지 않았으면 null 이며 0 으로 대체하지 않는다.
 */
public record DailyConcentration(LocalDate date, BigDecimal rate) {

    public boolean hasRate() {
        return rate != null;
    }
}
