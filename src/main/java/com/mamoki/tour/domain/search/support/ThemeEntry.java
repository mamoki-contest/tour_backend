package com.mamoki.tour.domain.search.support;

import java.util.List;

import com.mamoki.tour.domain.search.enums.SupportedTheme;

/**
 * 메모리에 올린 지원 테마 하나. {@code supported_theme} 한 행과 그 동의어를 합친 값이다.
 *
 * <p>테마는 요청마다 조회할 값이 아니라 기동 시 한 번 읽어 둔다. 그래서 여기 담기는
 * 값은 모두 불변이며, 다시 적재할 때는 목록을 통째로 갈아 끼운다.
 *
 * @param code        테마 코드. 응답 계약의 {@code code} 값이다.
 * @param displayName 사용자에게 보여줄 이름
 * @param aliases     정규화한 동의어. 검색어를 정규화해 이것과 맞춘다. 비면 어떤 검색어로도 이어지지 않는다.
 * @param keywords    공급자에게 실제로 보낼 검색어
 * @param matchTokens 추천 자격. 장소 이름에 이 중 하나가 들어가야 테마 결과로 담는다.
 *                    비면 어떤 장소도 자격을 얻지 못한다.
 */
public record ThemeEntry(SupportedTheme code, String displayName, List<String> aliases,
                         List<String> keywords, List<String> matchTokens) {

    public ThemeEntry {
        aliases = List.copyOf(aliases);
        keywords = List.copyOf(keywords);
        matchTokens = List.copyOf(matchTokens);
    }
}
