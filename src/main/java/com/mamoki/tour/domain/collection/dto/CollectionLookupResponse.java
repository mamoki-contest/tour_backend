package com.mamoki.tour.domain.collection.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 개인 컬렉션 일괄 재조회 응답.
 *
 * <p>백엔드는 저장된 식별자로 최신 표시정보를 되돌려주는 경계일 뿐이다. 컬렉션 자체와
 * 사용자 태그·메모·방문 예정일은 프론트 저장소가 관리하며 여기서 다루지 않는다.
 *
 * @param items 요청한 순서 그대로. 확인하지 못한 항목도 빠지지 않는다.
 */
@Schema(description = "저장된 식별자로 최신 표시정보를 일괄 재조회한 결과")
public record CollectionLookupResponse(

        @Schema(description = "요청한 순서 그대로의 항목별 결과")
        List<CollectionItemView> items,

        @Schema(description = "요청한 식별자 수", example = "12")
        int requestedCount,

        @Schema(description = "최신 표시정보를 얻은 수", example = "10")
        int availableCount,

        @Schema(description = "공급자에 더 이상 없는 수", example = "1")
        int notFoundCount,

        @Schema(description = """
                이번에 확인하지 못한 수. 없어진 항목이 아니므로 저장 목록에서 지우지 마세요""",
                example = "1")
        int unavailableCount,

        @Schema(description = "데이터 출처", example = "KorService2")
        String source
) {
}
