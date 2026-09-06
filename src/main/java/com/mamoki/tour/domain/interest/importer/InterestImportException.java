package com.mamoki.tour.domain.interest.importer;

/**
 * 관심도 파일이 검증을 통과하지 못했다.
 *
 * <p>이 예외가 나면 해당 적재는 통째로 거부된다. 일부만 적재해 두면 스냅샷이 부분 데이터가
 * 되어, 빠진 장소가 `관심도 없음`인지 `파일이 깨진 것`인지 구분할 수 없게 된다.
 */
public class InterestImportException extends RuntimeException {

    public InterestImportException(String msg) {
        super(msg);
    }

    public InterestImportException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
