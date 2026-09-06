package com.mamoki.tour.domain.mention.service;

import com.mamoki.tour.domain.mention.entity.OnlineMentionSnapshot;

/**
 * 수집 결과 요약.
 *
 * @param collected   정렬에 쓸 수 있는 값을 얻은 관광지 수
 * @param ambiguous   이름이 모호해 값을 신뢰할 수 없는 관광지 수
 * @param unavailable 검색어를 만들 수 없어 대상에서 빠진 관광지 수
 */
public record OnlineMentionCollectResult(
        OnlineMentionSnapshot snapshot,
        int target,
        int collected,
        int ambiguous,
        int unavailable
) {
}
