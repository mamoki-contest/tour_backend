package com.mamoki.tour.domain.region;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.region.dto.RegionVisitors;
import com.mamoki.tour.domain.region.enums.RegionVisitLevel;
import com.mamoki.tour.domain.region.support.RegionVisitLevels;

/**
 * 상대 구간 규칙 검증.
 *
 * <p>지도의 색을 정하는 값이라 같은 수치가 다른 색으로 갈리는 경우를 특히 확인한다.
 */
class RegionVisitLevelsTest {

    private static RegionVisitors region(String lawdCode, long visitorCount) {
        return new RegionVisitors(lawdCode, lawdCode + "시", visitorCount, 7);
    }

    @Test
    @DisplayName("강원 18개 시·군을 다섯 구간으로 나눈다")
    void splitsIntoFiveLevels() {
        List<RegionVisitors> regions = IntStream.rangeClosed(1, 18)
                .mapToObj(index -> region("511" + String.format("%02d", index), index * 1_000L))
                .toList();

        Map<String, RegionVisitLevel> levels = RegionVisitLevels.assign(regions);

        assertThat(levels).hasSize(18);
        assertThat(levels.values()).containsAll(List.of(RegionVisitLevel.values()));
    }

    @Test
    @DisplayName("방문자 수가 많은 시·군이 VERY_HIGH 를 받는다")
    void highestGetsTopLevel() {
        Map<String, RegionVisitLevel> levels = RegionVisitLevels.assign(List.of(
                region("51110", 100),
                region("51130", 500),
                region("51150", 300),
                region("51170", 50),
                region("51190", 10)));

        assertThat(levels.get("51130")).isEqualTo(RegionVisitLevel.VERY_HIGH);
        assertThat(levels.get("51190")).isEqualTo(RegionVisitLevel.VERY_LOW);
    }

    @Test
    @DisplayName("방문자 수가 같으면 같은 구간을 준다. 경계에서 색이 갈리지 않는다")
    void tiedRegionsShareLevel() {
        Map<String, RegionVisitLevel> levels = RegionVisitLevels.assign(List.of(
                region("51110", 100),
                region("51130", 100),
                region("51150", 100),
                region("51170", 100),
                region("51190", 100)));

        assertThat(levels.values()).containsOnly(RegionVisitLevel.VERY_HIGH);
    }

    @Test
    @DisplayName("값이 같으면 같은 순위를 준다")
    void tiedRegionsShareRank() {
        Map<String, Integer> ranks = RegionVisitLevels.rank(List.of(
                region("51110", 300),
                region("51130", 300),
                region("51150", 100)));

        assertThat(ranks.get("51110")).isEqualTo(1);
        assertThat(ranks.get("51130")).isEqualTo(1);
        assertThat(ranks.get("51150")).isEqualTo(3);
    }

    @Test
    @DisplayName("값이 없는 시·군은 최하 구간이 아니라 아예 담기지 않는다")
    void missingRegionsAreNotRanked() {
        Map<String, RegionVisitLevel> levels = RegionVisitLevels.assign(List.of(
                region("51110", 100),
                region("51130", 200)));

        assertThat(levels).containsOnlyKeys("51110", "51130");
        assertThat(levels).doesNotContainKey("51150");
    }

    @Test
    @DisplayName("집계가 하나도 없으면 구간도 없다")
    void emptyInputProducesNoLevels() {
        assertThat(RegionVisitLevels.assign(List.of())).isEmpty();
        assertThat(RegionVisitLevels.rank(List.of())).isEmpty();
    }

    @Test
    @DisplayName("시·군이 다섯보다 적어도 구간을 매긴다")
    void handlesFewerRegionsThanLevels() {
        Map<String, RegionVisitLevel> levels = RegionVisitLevels.assign(List.of(
                region("51110", 300),
                region("51130", 100)));

        assertThat(levels.get("51110")).isEqualTo(RegionVisitLevel.VERY_HIGH);
        assertThat(levels.get("51130")).isEqualTo(RegionVisitLevel.MEDIUM);
    }
}
