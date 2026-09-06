package com.mamoki.tour.infra.korservice.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * KorService2 의 원본 응답 구조.
 *
 * <p>공급자는 결과가 없을 때 items 를 빈 문자열("")로 내려주기도 한다.
 * 그 경우 Jackson 이 객체로 역직렬화하지 못하므로 호출 측에서 별도로 처리한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KorServiceResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {

        public boolean isSuccess() {
            return "0000".equals(resultCode);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, Integer numOfRows, Integer pageNo, Integer totalCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<KorServiceItem> item) {
    }

    public List<KorServiceItem> items() {
        if (response == null || response.body() == null || response.body().items() == null) {
            return List.of();
        }

        List<KorServiceItem> item = response.body().items().item();
        return item == null ? List.of() : item;
    }

    public int totalCount() {
        if (response == null || response.body() == null || response.body().totalCount() == null) {
            return 0;
        }

        return response.body().totalCount();
    }
}
