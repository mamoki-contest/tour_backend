package com.mamoki.tour.domain.attraction.dto;

import com.mamoki.tour.global.enums.TmapRankStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 시·군 내 TMAP 검색순위 보조 근거.
 *
 * <p>시·군 안에서만 의미가 있다. 시·군 사이의 절대 순위로 합치지 않으며 목록 정렬에도 쓰지 않는다.
 */
@Schema(description = "시·군 내 TMAP 검색순위. 보조 근거이며 목록 정렬에 쓰지 않는다.")
public record TmapRankView(

        @Schema(description = "AVAILABLE=순위 있음, NOT_AVAILABLE=파일에 수록되지 않음")
        TmapRankStatus status,

        @Schema(description = "시·군 내 순위. 수록되지 않았으면 null. 0 이나 낮은 순위로 대체하지 않는다.", example = "2")
        Integer rank,

        @Schema(description = "원천 조회기간", example = "202508-202607")
        String period
) {

    public static TmapRankView notAvailable() {
        return new TmapRankView(TmapRankStatus.NOT_AVAILABLE, null, null);
    }
}
