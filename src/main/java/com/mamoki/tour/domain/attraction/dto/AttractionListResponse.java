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
 * @param sort        요청한 정렬 기준. 적용 여부와 무관하게 요청한 값을 그대로 싣는다.
 * @param sortApplied 그 기준을 실제로 적용했는지. false 면 items 는 기본 순서 그대로다(#65).
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

        @Schema(description = "요청한 정렬 기준. 정렬을 요청하지 않았으면 null")
        AttractionSort sort,

        @Schema(description = """
                요청한 정렬 기준을 실제로 적용했는지.
                온라인 언급량이 산정된 장소가 하나도 없으면 줄 세울 기준이 없어 false 이며,
                이때 items 는 정렬하지 않은 기본 순서(카탈로그는 관광지명 오름차순,
                공급자 폴백은 공급자 순서)입니다. sort 가 null 이면 항상 false 입니다.""")
        boolean sortApplied,

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
        // 담을 항목이 없으니 정렬도 적용되지 않았다. 요청한 기준만 그대로 돌려준다.
        return new AttractionListResponse(List.of(), 0, page, size, sort, false,
                DataStatus.NO_DATA, null, source);
    }
}
