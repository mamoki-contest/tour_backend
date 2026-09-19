package com.mamoki.tour.domain.parking.importer;

/**
 * 주차장 표준데이터 적재를 중단시키는 오류.
 *
 * <p>이 예외가 오르면 새 스냅샷은 활성화되지 않고 직전 정상 스냅샷이 그대로 남는다.
 */
public class ParkingCatalogImportException extends RuntimeException {

    public ParkingCatalogImportException(String message) {
        super(message);
    }

    public ParkingCatalogImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
