package com.mamoki.tour.domain.attraction.importer;

/**
 * 카탈로그 적재 결과.
 *
 * @param fetched        공급자에서 받아온 항목 수
 * @param inserted       새로 담은 수
 * @param updated        이미 있어 새 값을 덮어쓴 수. 그중 <b>실제로 값이 달라진</b> 행이
 *                       몇인지는 세지 않는다. 내용이 같은 재적재에서는 이 수가 카탈로그
 *                       전체여도 DB 에 나가는 UPDATE 는 0건이다(#77).
 * @param regionUnmapped 법정동 매핑이 없어 지역을 비워 둔 수. 적재는 되었다.
 */
public record AttractionCatalogImportResult(int fetched, int inserted, int updated,
                                            int regionUnmapped) {

    public int saved() {
        return inserted + updated;
    }
}
