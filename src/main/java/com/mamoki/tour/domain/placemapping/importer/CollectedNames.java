package com.mamoki.tour.domain.placemapping.importer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 한 원천에서 모은 판정 대상과, 그 원천의 매칭률을 재는 데 필요한 수.
 *
 * <p>판정 대상은 <b>이름</b> 단위지만 매칭률은 <b>행</b> 단위다. 같은 이름이 여러 행에
 * 나타날 수 있어 이름 수로 매칭률을 재면 원천 파일이 실제로 얼마나 쓰이는지와 어긋난다.
 * 그래서 이름과 함께 그 이름이 몇 행인지를 들고 다닌다.
 *
 * @param names              판정할 이름들. 같은 이름은 한 번만 담는다.
 * @param totalRows          이 원천의 전체 행. 매칭률의 분모다.
 * @param matchedRows        이미 카탈로그에 이어진 행.
 * @param unmatchedRowsByKey 아직 잇지 못한 행 수를 {@link UnmatchedPlaceName#key()} 별로 센
 *                           것. 시·군을 몰라 판정할 수 없는 행은 담기지 않는다 — 그런 행은
 *                           분류되지 않은 채 분모에 남는다. 빼는 쪽으로 기울지 않는다.
 * @param matchedContentIds  이미 이 원천이 차지한 카탈로그 식별자. 표기 차이로 되찾을 때
 *                           이미 값이 붙어 있는 관광지에 두 번째 행을 붙이지 않으려고 쓴다.
 *                           한 관광지에 두 행이 붙으면 조회가 둘 중 아무거나 보여 주는데,
 *                           에러가 나지 않아 아무도 알아채지 못한다.
 */
public record CollectedNames(
        List<UnmatchedPlaceName> names,
        int totalRows,
        int matchedRows,
        Map<String, Integer> unmatchedRowsByKey,
        Set<String> matchedContentIds
) {

    public CollectedNames {
        names = List.copyOf(names);
        unmatchedRowsByKey = Map.copyOf(unmatchedRowsByKey);
        matchedContentIds = Set.copyOf(matchedContentIds);
    }

    /** 원천 데이터가 아직 없다. 매칭률을 잴 분모 자체가 없는 상태다. */
    public static CollectedNames empty() {
        return new CollectedNames(List.of(), 0, 0, Map.of(), Set.of());
    }

    /** 이름과 행 수를 함께 모으는 자리. 두 자료구조를 따로 들고 다니다 어긋나는 것을 막는다. */
    public static final class Builder {

        private final Map<String, UnmatchedPlaceName> byKey = new LinkedHashMap<>();
        private final Map<String, Integer> rowsByKey = new LinkedHashMap<>();
        private final Set<String> matchedContentIds = new LinkedHashSet<>();

        private int totalRows;
        private int matchedRows;
        private int unmatchedRows;

        /**
         * 이 원천의 전체 행. 매칭률의 분모다.
         *
         * <p>이어진 행과 잇지 못한 행을 더해서 세지 않는다. 둘 중 어느 쪽도 아닌 행이
         * 생기면(원천 적재가 저신뢰로 남긴 행) 분모가 조용히 줄어든다.
         */
        public Builder rows(int totalRows) {
            this.totalRows = totalRows;

            return this;
        }

        /** 이어진 행 하나. */
        public Builder matched(String contentId) {
            matchedRows++;

            if (contentId != null) {
                matchedContentIds.add(contentId);
            }

            return this;
        }

        /** 아직 잇지 못한 행 하나. 판정할 수 없는 이름이면 세기만 하고 대상에는 넣지 않는다. */
        public Builder unmatched(UnmatchedPlaceName name) {
            unmatchedRows++;

            if (!name.isDecidable()) {
                return this;
            }

            byKey.putIfAbsent(name.key(), name);
            rowsByKey.merge(name.key(), 1, Integer::sum);

            return this;
        }

        public CollectedNames build() {
            if (totalRows < matchedRows + unmatchedRows) {
                throw new IllegalStateException(
                        "분모(%d)가 이어진 행(%d)과 잇지 못한 행(%d)의 합보다 작습니다."
                                .formatted(totalRows, matchedRows, unmatchedRows));
            }

            return new CollectedNames(List.copyOf(byKey.values()), totalRows, matchedRows,
                    rowsByKey, matchedContentIds);
        }
    }
}
