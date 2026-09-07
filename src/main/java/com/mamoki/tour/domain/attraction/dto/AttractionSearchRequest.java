package com.mamoki.tour.domain.attraction.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mamoki.tour.domain.visittiming.enums.DateMode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 관광지 목록 조회 조건.
 *
 * <p>날짜 탐색은 선택 기능이다. {@code dateMode} 를 비우면 예측을 조회하지 않고 목록만 돌려준다.
 *
 * @param sigunguCode   관광공사 시·군구 코드. 비우면 강원 전체.
 * @param contentTypeId 관광지 분류. 비우면 전체(음식점·숙박 포함).
 * @param dateMode      FIXED(날짜 확정) / FLEXIBLE(날짜 유연). 비우면 날짜 탐색을 하지 않는다.
 * @param visitDate     확정 모드의 선택일. 확정 모드에서만 받는다.
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
        AttractionSort sort,

        @Schema(description = """
                날짜 탐색 모드. 비우면 날짜 탐색을 하지 않고 목록만 반환합니다.
                FIXED 는 visitDate 가 필요하고, FLEXIBLE 은 visitDate 를 받지 않습니다.""",
                example = "FIXED")
        DateMode dateMode,

        @Schema(description = "확정 모드의 선택일(yyyy-MM-dd). 유연 모드에서는 보내지 않습니다.",
                example = "2026-09-20")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @FutureOrPresent(message = "선택일은 오늘 이후여야 합니다.")
        LocalDate visitDate
) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;

    public int pageOrDefault() {
        return page == null ? DEFAULT_PAGE : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }

    /**
     * 확정 모드는 평가할 날짜가 있어야 성립한다. 없는 날짜를 오늘로 대신하지 않는다.
     *
     * <p>지원 범위(오늘부터 30일) 밖의 미래 날짜는 여기서 막지 않는다. 그쪽은 요청 형식 문제가
     * 아니라 안내할 내용이라, 200 응답에 {@code OUT_OF_RANGE} 와 지원 범위를 담아 돌려준다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "날짜를 확정하려면 선택일(visitDate)이 필요합니다.")
    public boolean isVisitDateRequiredWhenFixed() {
        return dateMode != DateMode.FIXED || visitDate != null;
    }

    /** 유연 모드는 날짜를 열어 두고 묻는 것이라 선택일을 받지 않는다. 받으면 어느 쪽을 물은 것인지 모호해진다. */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "유연 모드에서는 선택일(visitDate)을 보내지 않습니다.")
    public boolean isVisitDateAbsentWhenFlexible() {
        return dateMode != DateMode.FLEXIBLE || visitDate == null;
    }
}
