package com.mamoki.tour.infra.naver.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 네이버 블로그 검색 응답.
 *
 * <p>우리가 쓰는 값은 {@code total} 하나다. 항목 목록은 계약 확인과 진단 용도로만 둔다.
 *
 * @param total 검색 결과 수. 블로그 글 수이며 실제 방문객 수·검색량·현재 혼잡이 아니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverBlogSearchResponse(
        String lastBuildDate,
        Long total,
        Integer start,
        Integer display,
        List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String title, String link, String postdate) {
    }

    /** 응답에 total 이 없으면 값을 만들어내지 않는다. */
    public boolean hasTotal() {
        return total != null;
    }
}
