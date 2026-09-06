package com.mamoki.tour.infra.locgohub.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocgoHubResponse(Response response) {

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
    public record Items(List<LocgoHubItem> item) {
    }

    public List<LocgoHubItem> items() {
        if (response == null || response.body() == null || response.body().items() == null) {
            return List.of();
        }

        List<LocgoHubItem> item = response.body().items().item();
        return item == null ? List.of() : item;
    }

    public int totalCount() {
        if (response == null || response.body() == null || response.body().totalCount() == null) {
            return 0;
        }

        return response.body().totalCount();
    }
}
