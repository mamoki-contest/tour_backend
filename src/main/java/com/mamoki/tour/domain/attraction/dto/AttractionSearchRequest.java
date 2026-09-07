package com.mamoki.tour.domain.attraction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 관광지 목록 조회 조건.
 *
 * @param sigunguCode   관광공사 시·군구 코드. 비우면 강원 전체.
 * @param contentTypeId 관광지 분류. 비우면 전체(음식점·숙박 포함).
 */
@Schema(description = "관광지 목록 조회 조건")
public record AttractionSearchRequest(

        @Pattern(regexp = "\\d{1,2}", message = "시·군구 코드는 1~2자리 숫자여야 합니다.")
        String sigunguCode,

        @Pattern(regexp = "\\d{1,2}", message = "분류 코드는 1~2자리 숫자여야 합니다.")
        String contentTypeId,

        @Schema(description = "페이지 번호. 기본 1", example = "1")
        @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
        Integer page,

        @Min(value = 1, message = "조회 개수는 1 이상이어야 합니다.")
        @Schema(description = "조회 개수. 기본 20, 최대 100", example = "20")
        @Max(value = 100, message = "조회 개수는 100 이하여야 합니다.")
        Integer size,

        @Schema(description = "정렬 기준. 비우면 공급자 순서를 그대로 사용합니다.")
        AttractionSort sort
) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;

    public int pageOrDefault() {
        return page == null ? DEFAULT_PAGE : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }
}
