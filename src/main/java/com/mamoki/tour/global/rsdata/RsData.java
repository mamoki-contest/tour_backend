package com.mamoki.tour.global.rsdata;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API 응답이 공유하는 공통 봉투.
 *
 * <p>resultCode 는 {HTTP 상태}-{일련번호} 형식이며, 앞자리에서 statusCode 를 파생한다.
 * 컨트롤러는 이 statusCode 를 실제 HTTP 응답 상태와 항상 일치시켜야 한다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RsData<T>(
        @Schema(description = "{HTTP 상태}-{일련번호} 형식의 결과 코드", example = "200-1")
        String resultCode,

        @Schema(description = "HTTP 응답 상태와 항상 일치합니다.", example = "200")
        int statusCode,

        @Schema(description = "사람이 읽는 메시지. 프론트 분기 조건으로 쓰지 마세요.")
        String msg,

        @Schema(description = "성공 시 페이로드, 실패 시 null 또는 검증 오류 상세")
        T data
) {

    public RsData(String resultCode, String msg, T data) {
        this(resultCode, parseStatusCode(resultCode), msg, data);
    }

    public static <T> RsData<T> of(String resultCode, String msg, T data) {
        return new RsData<>(resultCode, msg, data);
    }

    public static RsData<Void> of(String resultCode, String msg) {
        return new RsData<>(resultCode, msg, null);
    }

    /** 판별용 편의 메서드. 응답 계약에는 포함하지 않는다. */
    @JsonIgnore
    public boolean isSuccess() {
        return statusCode < 400;
    }

    @JsonIgnore
    public boolean isFail() {
        return !isSuccess();
    }

    private static int parseStatusCode(String resultCode) {
        if (resultCode == null || resultCode.isBlank()) {
            throw new IllegalArgumentException("resultCode 는 비어 있을 수 없습니다.");
        }

        int separatorIndex = resultCode.indexOf('-');
        String statusPart = separatorIndex < 0 ? resultCode : resultCode.substring(0, separatorIndex);

        try {
            return Integer.parseInt(statusPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "resultCode 앞자리는 HTTP 상태 코드여야 합니다: " + resultCode, e);
        }
    }
}
