package com.mamoki.tour.infra.gnits.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 강릉시 교통정보 조회서비스 응답 헤더.
 *
 * <p>결과 코드는 문자열이다. {@code 00} 정상 / {@code 02} DB_ERROR / {@code 03} NO_DATA /
 * {@code 10} 파라미터 오류 / {@code 11} 필수 파라미터 누락 / {@code 99} UNKNOWN.
 *
 * <p>HTTP 200 이어도 이 코드가 {@code 00} 이 아니면 실패다. 게이트웨이 층 오류는 아예
 * 다른 모양({@code OpenAPI_ServiceResponse})으로 와서 이 헤더 자체가 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GnItsHeader(String resultCode, String resultMsg) {

    public boolean isSuccess() {
        return "00".equals(resultCode);
    }
}
