package com.mamoki.tour.domain.visitorstats.importer;

/** 입장객통계 파일이 기대한 형식과 다르거나 검증을 통과하지 못한 경우. */
public class VisitorStatsImportException extends RuntimeException {

    public VisitorStatsImportException(String message) {
        super(message);
    }

    public VisitorStatsImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
