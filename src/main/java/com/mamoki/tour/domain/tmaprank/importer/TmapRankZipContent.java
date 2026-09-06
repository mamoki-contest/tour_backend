package com.mamoki.tour.domain.tmaprank.importer;

import java.util.List;

/**
 * 검증을 통과한 zip 하나의 내용.
 *
 * @param source       파일명에서 읽은 지역과 원천 조회기간
 * @param allAgesRows  전체 연령 파일의 행. 순위 값으로 적재하는 대상이다.
 * @param ageGroups    zip 안에서 확인된 연령대 목록. 온전성 검증에만 쓴다.
 */
public record TmapRankZipContent(
        TmapRankFileName source,
        List<TmapRankCsvRow> allAgesRows,
        List<String> ageGroups
) {
}
