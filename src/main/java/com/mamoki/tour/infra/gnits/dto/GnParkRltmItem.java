package com.mamoki.tour.infra.gnits.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 강릉시 주차장 실시간 현황 한 곳.
 *
 * <p><b>{@code availLots} 는 이름과 달리 잔여면이 아니라 점유 대수다.</b> 강릉시 ITS 원천의
 * {@code occupancy} 와 값이 일치한다. 그대로 잔여면으로 쓰면 만차를 텅 빈 것으로,
 * 텅 빈 곳을 만차로 보여준다. 잔여면은 {@code totalLots - availLots} 로 계산한다.
 *
 * <p>그래서 이 DTO 에서 이름을 {@link #occupiedLots()} 로 바꿔 부른다. 원본 필드명을 그대로
 * 들고 다니면 쓰는 쪽에서 매번 다시 헷갈린다.
 *
 * <p>숫자가 전부 문자열로 온다. 기준시각 필드는 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GnParkRltmItem(
        String prkId,
        String prkName,
        String totalLots,
        String availLots
) {

    /** 공급자 필드 {@code availLots} 의 실제 의미. 점유 대수다. */
    public String occupiedLots() {
        return availLots;
    }
}
