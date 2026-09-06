package com.mamoki.tour.global.exception;

/**
 * 요청 검증 실패 시 반환하는 필드 단위 오류 상세.
 */
public record FieldErrorDetail(String field, String msg) {
}
