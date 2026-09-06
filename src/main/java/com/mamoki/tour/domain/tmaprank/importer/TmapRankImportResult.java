package com.mamoki.tour.domain.tmaprank.importer;

import com.mamoki.tour.domain.tmaprank.entity.TmapRankSnapshot;

/**
 * 적재 결과 요약.
 *
 * <p>matched 와 unmatched 는 표준 관광지 카탈로그와의 매칭 결과다. 카탈로그가 비어 있으면
 * 모두 unmatched 가 되며, 이는 순위가 없다는 뜻이 아니라 아직 이을 대상이 없다는 뜻이다.
 */
public record TmapRankImportResult(
        TmapRankSnapshot snapshot,
        int regionCount,
        int totalRows,
        int matchedRows,
        int unmatchedRows
) {
}
