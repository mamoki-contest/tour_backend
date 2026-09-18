package com.mamoki.tour.domain.visitorstats.importer;

import com.mamoki.tour.domain.visitorstats.entity.VisitorStatsSnapshot;

/**
 * 적재 결과 요약.
 *
 * @param totalRows    공표월에 값이 있던 관광지 수
 * @param matchedRows  카탈로그 식별자에 이어 붙은 수
 * @param unmatchedRows 이어 붙이지 못한 수. 값은 남지만 조회에 노출되지 않는다.
 */
public record VisitorStatsImportResult(VisitorStatsSnapshot snapshot, int totalRows,
                                       int matchedRows, int unmatchedRows) {
}
