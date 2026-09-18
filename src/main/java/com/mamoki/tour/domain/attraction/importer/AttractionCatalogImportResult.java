package com.mamoki.tour.domain.attraction.importer;

/**
 * 카탈로그 적재 결과.
 *
 * @param fetched        공급자에서 받아온 항목 수
 * @param inserted       새로 담은 수
 * @param updated        이미 있어 갱신한 수
 * @param regionUnmapped 법정동 매핑이 없어 지역을 비워 둔 수. 적재는 되었다.
 */
public record AttractionCatalogImportResult(int fetched, int inserted, int updated,
                                            int regionUnmapped) {

    public int saved() {
        return inserted + updated;
    }
}
