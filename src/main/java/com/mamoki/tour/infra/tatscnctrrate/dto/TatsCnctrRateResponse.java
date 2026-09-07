package com.mamoki.tour.infra.tatscnctrrate.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** TatsCnctrRateService 의 원본 응답 구조. 공공데이터포털 B551011 계열 공통 봉투를 따른다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TatsCnctrRateResponse(Response response) {

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
    public record Items(List<TatsCnctrRateItem> item) {
    }

    public List<TatsCnctrRateItem> items() {
        if (response == null || response.body() == null || response.body().items() == null) {
            return List.of();
        }

        List<TatsCnctrRateItem> item = response.body().items().item();
        return item == null ? List.of() : item;
    }

    public int totalCount() {
        if (response == null || response.body() == null || response.body().totalCount() == null) {
            return 0;
        }

        return response.body().totalCount();
    }
}
