package com.mamoki.tour.domain.search;

import java.util.List;

import com.mamoki.tour.domain.search.enums.SupportedTheme;
import com.mamoki.tour.domain.search.support.ThemeEntry;

/**
 * {@code data.sql} 의 지원 테마 시드를 그대로 옮긴 목록.
 *
 * <p>DB 없이 도는 단위 테스트가 쓴다. 여기와 시드가 어긋나면 단위 테스트는 그대로
 * 통과하면서 실제 동작만 달라지는데, 그것이 이 프로젝트에서 가장 늦게 드러나는 종류의
 * 실패다. 그래서 {@code SupportedThemeSeedTest} 가 시드를 읽어 이 목록과 <b>그대로 같은지</b>
 * 확인한다. 시드를 고치면 여기도 고쳐야 그 테스트가 통과한다.
 */
final class ThemeFixtures {

    private ThemeFixtures() {
    }

    static List<ThemeEntry> seedThemes() {
        return List.of(
                new ThemeEntry(SupportedTheme.CHERRY_BLOSSOM, "벚꽃",
                        List.of("벚꽃", "벚꽃길", "벚꽃축제", "벚나무", "cherryblossom"),
                        List.of("벚꽃"), List.of("벚꽃")),
                new ThemeEntry(SupportedTheme.FLOWER_FESTIVAL, "꽃축제",
                        List.of("꽃축제", "꽃놀이", "플라워축제", "flowerfestival"),
                        List.of("꽃"), List.of("꽃")),
                new ThemeEntry(SupportedTheme.BEACH, "해수욕장",
                        List.of("해수욕장", "해변", "바닷가", "비치", "해수욕", "beach"),
                        List.of("해수욕장", "해변"), List.of("해수욕장", "해변")),
                new ThemeEntry(SupportedTheme.VALLEY, "계곡",
                        List.of("계곡", "계곡물", "valley"),
                        List.of("계곡"), List.of("계곡")),
                new ThemeEntry(SupportedTheme.AUTUMN_FOLIAGE, "단풍",
                        List.of("단풍", "단풍놀이", "가을단풍", "autumnleaves"),
                        List.of("단풍"), List.of("단풍")),
                new ThemeEntry(SupportedTheme.SILVER_GRASS, "억새",
                        List.of("억새", "억새밭", "억새풀", "silvergrass"),
                        List.of("억새"), List.of("억새")),
                new ThemeEntry(SupportedTheme.SNOW_FLOWER, "눈꽃",
                        List.of("눈꽃", "눈꽃축제", "설경", "snowflower"),
                        List.of("눈꽃"), List.of("눈꽃")),
                new ThemeEntry(SupportedTheme.SUNRISE, "해돋이",
                        List.of("해돋이", "일출", "해뜨는곳", "sunrise"),
                        List.of("해돋이", "일출"), List.of("해돋이", "일출")));
    }
}
