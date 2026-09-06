package com.mamoki.tour.global.rsdata;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API 응답이 공유하는 공통 봉투.
 *
 * <p>resultCode 는 {HTTP 상태}-{일련번호} 형식이며, 앞자리에서 statusCode 를 파생한다.
 * 컨트롤러는 이 statusCode 를 실제 HTTP 응답 상태와 항상 일치시켜야 한다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RsData<T>(
        String resultCode,
        int statusCode,
        String msg,
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

    public boolean isSuccess() {
        return statusCode < 400;
    }

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
