package com.mamoki.tour.domain.search.enums;

import java.util.List;

/**
 * 검증된 추천을 보장하는 지원 테마.
 *
 * <p>여기 없는 입력은 지원 테마가 아니다. 비슷해 보인다는 이유로 늘리지 않는다.
 * 지원 테마라는 표시는 추천 자격을 적용했다는 뜻이고, 자격을 확인할 수 없는 말에
 * 그 표시를 붙이면 보증이 의미를 잃는다.
 *
 * @param displayName 사용자에게 보여줄 이름
 * @param aliases     같은 뜻으로 받아들일 말. 정규화한 검색어를 여기에 맞춘다.
 * @param keywords    공급자에게 실제로 보낼 검색어. 여럿이면 합쳐서 중복을 지운다.
 * @param matchTokens 추천 자격. 장소 이름에 이 중 하나가 들어가야 테마 결과로 담는다.
 */
public enum SupportedTheme {

    CHERRY_BLOSSOM("벚꽃",
            List.of("벚꽃", "벚꽃길", "벚꽃축제", "벚나무", "cherryblossom"),
            List.of("벚꽃"),
            List.of("벚꽃")),

    FLOWER_FESTIVAL("꽃축제",
            List.of("꽃축제", "꽃 축제", "꽃놀이", "플라워축제", "flowerfestival"),
            List.of("꽃"),
            List.of("꽃")),

    BEACH("해수욕장",
            List.of("해수욕장", "해변", "바닷가", "비치", "해수욕", "beach"),
            List.of("해수욕장", "해변"),
            List.of("해수욕장", "해변")),

    VALLEY("계곡",
            List.of("계곡", "계곡물", "valley"),
            List.of("계곡"),
            List.of("계곡")),

    AUTUMN_FOLIAGE("단풍",
            List.of("단풍", "단풍놀이", "가을단풍", "autumnleaves"),
            List.of("단풍"),
            List.of("단풍")),

    SILVER_GRASS("억새",
            List.of("억새", "억새밭", "억새풀", "silvergrass"),
            List.of("억새"),
            List.of("억새")),

    SNOW_FLOWER("눈꽃",
            List.of("눈꽃", "눈꽃축제", "설경", "snowflower"),
            List.of("눈꽃"),
            List.of("눈꽃")),

    SUNRISE("해돋이",
            List.of("해돋이", "일출", "해뜨는곳", "sunrise"),
            List.of("해돋이", "일출"),
            List.of("해돋이", "일출"));

    private final String displayName;
    private final List<String> aliases;
    private final List<String> keywords;
    private final List<String> matchTokens;

    SupportedTheme(String displayName, List<String> aliases,
                   List<String> keywords, List<String> matchTokens) {
        this.displayName = displayName;
        this.aliases = aliases;
        this.keywords = keywords;
        this.matchTokens = matchTokens;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> aliases() {
        return aliases;
    }

    public List<String> keywords() {
        return keywords;
    }

    public List<String> matchTokens() {
        return matchTokens;
    }
}
