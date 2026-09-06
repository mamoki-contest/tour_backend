package com.mamoki.tour.domain.tmaprank.importer;

/**
 * TMAP 검색순위 파일이 검증을 통과하지 못했다.
 *
 * <p>이 예외가 나면 해당 적재는 통째로 거부된다. 일부만 적재해 두면 스냅샷이 부분 데이터가
 * 되어, 빠진 장소가 `순위 미수록`인지 `파일이 깨진 것`인지 구분할 수 없게 된다.
 */
public class TmapRankImportException extends RuntimeException {

    public TmapRankImportException(String msg) {
        super(msg);
    }

    public TmapRankImportException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
