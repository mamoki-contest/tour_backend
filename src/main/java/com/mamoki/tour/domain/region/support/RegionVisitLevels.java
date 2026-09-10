package com.mamoki.tour.domain.region.support;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mamoki.tour.domain.region.dto.RegionVisitors;
import com.mamoki.tour.domain.region.enums.RegionVisitLevel;

/**
 * 방문 규모를 시·군끼리 비교한 상대 구간으로 바꾼다.
 *
 * <p>값이 있는 시·군만 줄을 세우고 다섯 구간으로 고르게 자른다. 절대 기준선을 두지 않는 것은
 * 방문자 수의 자릿수가 시·군마다 크게 달라 고정 경계로는 지도에서 색이 한쪽으로 몰리기 때문이다.
 *
 * <p>값이 같으면 같은 구간을 준다. 경계에 걸렸다는 이유로 같은 수치가 다른 색으로 보이면
 * 지도를 읽는 쪽에서는 오류로 보인다.
 */
public final class RegionVisitLevels {

    private static final RegionVisitLevel[] LEVELS = {
            RegionVisitLevel.VERY_HIGH,
            RegionVisitLevel.HIGH,
            RegionVisitLevel.MEDIUM,
            RegionVisitLevel.LOW,
            RegionVisitLevel.VERY_LOW
    };

    private RegionVisitLevels() {
    }

    /**
     * 방문자 수가 많은 쪽부터 구간을 매긴다.
     *
     * @return 법정동 시·군구 코드 → 상대 구간. 입력에 없던 시·군은 담기지 않는다.
     */
    public static Map<String, RegionVisitLevel> assign(List<RegionVisitors> regions) {
        if (regions.isEmpty()) {
            return Map.of();
        }

        List<RegionVisitors> ordered = regions.stream()
                .sorted(Comparator.comparingLong(RegionVisitors::visitorCount).reversed()
                        .thenComparing(RegionVisitors::lawdCode))
                .toList();

        Map<String, RegionVisitLevel> levels = new LinkedHashMap<>();
        RegionVisitLevel previousLevel = null;
        Long previousCount = null;

        for (int index = 0; index < ordered.size(); index++) {
            RegionVisitors region = ordered.get(index);

            RegionVisitLevel level = previousCount != null && previousCount == region.visitorCount()
                    ? previousLevel
                    : LEVELS[index * LEVELS.length / ordered.size()];

            levels.put(region.lawdCode(), level);
            previousLevel = level;
            previousCount = region.visitorCount();
        }

        return levels;
    }

    /**
     * 방문자 수가 많은 쪽부터 1위를 매긴다.
     *
     * @return 법정동 시·군구 코드 → 순위. 값이 같으면 같은 순위를 준다.
     */
    public static Map<String, Integer> rank(List<RegionVisitors> regions) {
        List<RegionVisitors> ordered = regions.stream()
                .sorted(Comparator.comparingLong(RegionVisitors::visitorCount).reversed()
                        .thenComparing(RegionVisitors::lawdCode))
                .toList();

        Map<String, Integer> ranks = new LinkedHashMap<>();
        int rank = 0;
        Long previousCount = null;

        for (int index = 0; index < ordered.size(); index++) {
            RegionVisitors region = ordered.get(index);

            if (previousCount == null || previousCount != region.visitorCount()) {
                rank = index + 1;
            }

            ranks.put(region.lawdCode(), rank);
            previousCount = region.visitorCount();
        }

        return ranks;
    }
}
