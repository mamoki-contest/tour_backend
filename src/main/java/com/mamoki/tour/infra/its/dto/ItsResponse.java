package com.mamoki.tour.infra.its.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ITS 응답 구조.
 *
 * <p>공공데이터포털 계열과 달리 {@code header} / {@code body} 가 최상위에 바로 온다.
 * 성공 코드도 문자열 {@code "0000"} 이 아니라 숫자 {@code 0} 이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItsResponse(Header header, Body body) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(Integer resultCode, String resultMsg) {

        public boolean isSuccess() {
            return resultCode != null && resultCode == 0;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Integer totalCount, List<ItsItem> items) {
    }

    public List<ItsItem> items() {
        if (body == null || body.items() == null) {
            return List.of();
        }

        return body.items();
    }

    public int totalCount() {
        if (body == null || body.totalCount() == null) {
            return 0;
        }

        return body.totalCount();
    }
}
