package com.mamoki.tour.global.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 요청 검증 실패 시 반환하는 필드 단위 오류 상세.
 */
@Schema(description = "요청 검증 실패 시 필드별 오류")
public record FieldErrorDetail(

        @Schema(description = "오류가 발생한 필드명", example = "size")
        String field,

        @Schema(description = "오류 사유", example = "조회 개수는 100 이하여야 합니다.")
        String msg
) {
}
