package com.mamoki.tour.domain.attraction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

import com.mamoki.tour.global.enums.DataStatus;

/**
 * 관광지 목록 응답.
 *
 * <p>공급자 장애나 결측을 빈 목록으로 위장하지 않는다. dataStatus 로 상태를 구분하고,
 * collectedAt 으로 이 데이터를 언제 수집했는지 함께 알린다.
 *
 * @param dataStatus  AVAILABLE(유효) / STALE(최종 정상 데이터) / NO_DATA(정보 없음)
 * @param collectedAt 응답에 사용한 데이터의 수집 시각. NO_DATA 이면 null.
 */
@Schema(description = "관광지 목록 응답. 공급자 장애를 빈 목록으로 위장하지 않습니다.")
public record AttractionListResponse(

        @Schema(description = "조회된 관광지 목록")
        List<AttractionResponse> items,

        @Schema(description = "조건에 해당하는 전체 건수", example = "95")
        int totalCount,

        @Schema(description = "요청한 페이지 번호", example = "1")
        int page,

        @Schema(description = "요청한 조회 개수", example = "20")
        int size,

        @Schema(description = "적용된 정렬 기준. 정렬을 요청하지 않았으면 null")
        AttractionSort sort,

        @Schema(description = """
                데이터 상태.
                AVAILABLE=유효, STALE=갱신 실패로 최종 정상 데이터 사용, NO_DATA=정보 없음""")
        DataStatus dataStatus,

        @Schema(description = "응답에 사용한 데이터의 수집 시각. NO_DATA 이면 null")
        LocalDateTime collectedAt,

        @Schema(description = "데이터 출처", example = "KorService2")
        String source
) {

    public static AttractionListResponse noData(int page, int size, String source, AttractionSort sort) {
        return new AttractionListResponse(List.of(), 0, page, size, sort,
                DataStatus.NO_DATA, null, source);
    }
}
