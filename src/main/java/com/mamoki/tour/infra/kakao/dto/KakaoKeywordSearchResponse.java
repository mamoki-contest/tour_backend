package com.mamoki.tour.infra.kakao.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 카카오 로컬 키워드 검색 응답.
 *
 * <p>검색 결과가 없으면 {@code documents} 가 빈 목록으로 온다. 이 빈 목록은 실패가 아니라
 * "카카오도 이 이름을 모른다" 는 사실이며, 매핑에서는 {@code UNMATCHED} 로 남는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoKeywordSearchResponse(
        List<KakaoPlace> documents,
        Meta meta
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(Integer total_count, Integer pageable_count, Boolean is_end) {
    }

    public KakaoKeywordSearchResponse {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }
}
