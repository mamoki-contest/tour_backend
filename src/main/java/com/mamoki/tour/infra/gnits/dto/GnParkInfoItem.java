package com.mamoki.tour.infra.gnits.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 강릉시 주차장 기본정보 한 곳.
 *
 * <p><b>{@code xCrdn} 이 경도, {@code yCrdn} 이 위도다.</b> x·y 라는 이름 때문에 바꿔 읽기
 * 쉬운데, 바꿔 읽으면 강릉의 주차장이 중국 산둥반도 앞바다에 찍힌다.
 *
 * <p>운영시각은 {@code "0800"} 같은 HHmm 4자리가 보통이지만 {@code "0"} 한 자리로 오는
 * 곳도 있다. 고정 길이로 자르면 안 된다. {@code "2300"} 은 23시 종료이지 24시간 운영이 아니다.
 *
 * @param prkId   주차장 식별자. 실시간 응답과 이 값으로 잇는다. 번호가 연속하지 않는다
 * @param prkType 공영 / 민영
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GnParkInfoItem(
        String prkId,
        String prkName,
        String prkAddr,
        String xCrdn,
        String yCrdn,
        String prkType,
        String weekOpenTime,
        String weekEndTime,
        String satOpenTime,
        String satEndTime,
        String holiOpenTime,
        String holiEndTime
) {
}
