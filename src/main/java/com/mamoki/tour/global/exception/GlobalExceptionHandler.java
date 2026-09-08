package com.mamoki.tour.global.exception;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.mamoki.tour.global.rsdata.ResultCodes;
import com.mamoki.tour.global.rsdata.RsData;

import jakarta.validation.ConstraintViolationException;

/**
 * 모든 예외를 {@link RsData} 봉투로 변환하는 전역 처리기.
 *
 * <p>HTTP 응답 상태는 항상 {@code RsData.statusCode} 와 일치시킨다.
 * 내부 오류의 스택트레이스나 외부 공급자 응답 원문은 클라이언트에 노출하지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 스프링이 타입 변환 실패에 붙이는 오류 코드. */
    private static final String TYPE_MISMATCH_CODE = "typeMismatch";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<RsData<Void>> handleServiceException(ServiceException e) {
        RsData<Void> rsData = e.getRsData();
        return ResponseEntity.status(rsData.statusCode()).body(rsData);
    }

    /**
     * {@code @RequestBody} 검증 실패(MethodArgumentNotValidException)와
     * {@code @ModelAttribute} 바인딩 실패를 함께 처리한다. 전자는 BindException 의 하위 타입이다.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<RsData<List<FieldErrorDetail>>> handleBindException(BindException e) {

        List<FieldErrorDetail> details = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toDetail)
                .sorted(Comparator.comparing(FieldErrorDetail::field))
                .toList();

        RsData<List<FieldErrorDetail>> rsData =
                RsData.of(ResultCodes.INVALID_REQUEST, "요청 값이 올바르지 않습니다.", details);

        return ResponseEntity.status(rsData.statusCode()).body(rsData);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RsData<Void>> handleConstraintViolation(ConstraintViolationException e) {
        RsData<Void> rsData = RsData.of(ResultCodes.CONSTRAINT_VIOLATION, "요청 값이 올바르지 않습니다.");
        return ResponseEntity.status(rsData.statusCode()).body(rsData);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RsData<Void>> handleMissingParameter(
            MissingServletRequestParameterException e) {

        RsData<Void> rsData =
                RsData.of(ResultCodes.MISSING_PARAMETER, "필수 파라미터가 누락되었습니다: " + e.getParameterName());

        return ResponseEntity.status(rsData.statusCode()).body(rsData);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RsData<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        RsData<Void> rsData =
                RsData.of(ResultCodes.TYPE_MISMATCH, "파라미터 형식이 올바르지 않습니다: " + e.getName());

        return ResponseEntity.status(rsData.statusCode()).body(rsData);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RsData<Void>> handleNoResourceFound(NoResourceFoundException e) {
        RsData<Void> rsData = RsData.of(ResultCodes.NOT_FOUND, "요청한 경로를 찾을 수 없습니다.");
        return ResponseEntity.status(rsData.statusCode()).body(rsData);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RsData<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);

        RsData<Void> rsData = RsData.of(ResultCodes.INTERNAL_ERROR, "서버 내부 오류가 발생했습니다.");
        return ResponseEntity.status(rsData.statusCode()).body(rsData);
    }

    /**
     * 타입 변환 실패는 스프링이 만든 메시지를 그대로 쓰지 않는다.
     *
     * <p>그 메시지에는 대상 타입의 전체 클래스명이 들어 있어 내부 패키지 구조가 그대로 나간다.
     * 프론트가 사용자에게 보여줄 수 있는 문구도 아니다. 어느 필드가 잘못됐는지만 알리면 충분하다.
     */
    private static FieldErrorDetail toDetail(FieldError fieldError) {
        if (TYPE_MISMATCH_CODE.equals(fieldError.getCode())) {
            return new FieldErrorDetail(fieldError.getField(), "형식이 올바르지 않습니다.");
        }

        String msg = fieldError.getDefaultMessage();
        return new FieldErrorDetail(fieldError.getField(), msg == null ? "올바르지 않은 값입니다." : msg);
    }
}
