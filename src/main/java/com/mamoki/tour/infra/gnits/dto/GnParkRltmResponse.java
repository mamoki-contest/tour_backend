package com.mamoki.tour.infra.gnits.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code getParkRltm} 응답. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GnParkRltmResponse(GnItsHeader header, Body body) implements GnItsEnvelope {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Integer pageNo, Integer numOfRows, Integer totalCount, Items items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<GnParkRltmItem> item) {
    }

    public List<GnParkRltmItem> items() {
        if (body == null || body.items() == null || body.items().item() == null) {
            return List.of();
        }

        return body.items().item();
    }

    @Override
    public int totalCount() {
        return body == null || body.totalCount() == null ? 0 : body.totalCount();
    }
}
