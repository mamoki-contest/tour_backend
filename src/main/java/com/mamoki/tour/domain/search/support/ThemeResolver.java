package com.mamoki.tour.domain.search.support;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mamoki.tour.domain.search.enums.SupportedTheme;

/**
 * 검색어를 지원 테마로 잇는다.
 *
 * <p>정규화한 뒤 동의어와 정확히 맞는지 먼저 보고, 그다음 한 글자 차이까지만 오타로 본다.
 * 두 글자 이상 다르면 다른 말로 취급한다. 넉넉하게 잡을수록 엉뚱한 검색어가 지원 테마로
 * 둔갑하고, 그 순간 지원 테마라는 표시가 보증하는 것이 없어진다.
 */
public final class ThemeResolver {

    /** 오타로 인정할 최대 편집 거리. */
    private static final int MAX_TYPO_DISTANCE = 1;

    /** 한 글자만 남는 말은 오타 판정에서 뺀다. 짧을수록 다른 말과 우연히 겹친다. */
    private static final int MIN_TYPO_LENGTH = 2;

    /** 제안으로 내보낼 최대 거리. 이보다 멀면 가까운 테마가 없다고 본다. */
    private static final int MAX_SUGGESTION_DISTANCE = 3;

    private static final int MAX_SUGGESTIONS = 3;

    private ThemeResolver() {
    }

    /** 공백과 대소문자를 지운다. 지운 결과가 비면 null 이다. */
    public static String normalize(String query) {
        if (query == null) {
            return null;
        }

        String normalized = query.replaceAll("\\s+", "").toLowerCase(Locale.KOREAN);
        return normalized.isEmpty() ? null : normalized;
    }

    /** 정규화한 검색어가 지원 테마와 같거나 한 글자 차이면 그 테마로 본다. */
    public static Optional<SupportedTheme> resolve(String query) {
        String normalized = normalize(query);

        if (normalized == null) {
            return Optional.empty();
        }

        for (SupportedTheme theme : SupportedTheme.values()) {
            if (matchesExactly(theme, normalized)) {
                return Optional.of(theme);
            }
        }

        if (normalized.length() < MIN_TYPO_LENGTH) {
            return Optional.empty();
        }

        return nearest(normalized)
                .filter(match -> match.distance() <= MAX_TYPO_DISTANCE)
                .map(Match::theme);
    }

    /**
     * 지원 테마로 잇지 못한 검색어에 가까운 테마를 제안한다.
     *
     * <p>결과가 비었을 때 대신 보여줄 것이 있으면 보여주려는 목적이지, 검색 결과를
     * 대신하는 값이 아니다. 멀면 빈 목록을 돌려주고 억지로 채우지 않는다.
     */
    public static List<SupportedTheme> suggest(String query) {
        String normalized = normalize(query);

        if (normalized == null) {
            return List.of();
        }

        return java.util.Arrays.stream(SupportedTheme.values())
                .map(theme -> new Match(theme, bestDistance(theme, normalized)))
                .filter(match -> match.distance() <= MAX_SUGGESTION_DISTANCE)
                .sorted(Comparator.comparingInt(Match::distance)
                        .thenComparing(match -> match.theme().name()))
                .limit(MAX_SUGGESTIONS)
                .map(Match::theme)
                .toList();
    }

    private static boolean matchesExactly(SupportedTheme theme, String normalized) {
        return theme.aliases().stream()
                .map(ThemeResolver::normalize)
                .anyMatch(normalized::equals);
    }

    private static Optional<Match> nearest(String normalized) {
        return java.util.Arrays.stream(SupportedTheme.values())
                .map(theme -> new Match(theme, bestDistance(theme, normalized)))
                .min(Comparator.comparingInt(Match::distance)
                        .thenComparing(match -> match.theme().name()));
    }

    private static int bestDistance(SupportedTheme theme, String normalized) {
        return theme.aliases().stream()
                .map(ThemeResolver::normalize)
                .mapToInt(alias -> distance(alias, normalized))
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    /** 레벤슈타인 거리. 후보가 여덟 테마의 동의어뿐이라 단순 구현으로 충분하다. */
    static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];

        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;

            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1]
                        + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);

                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }

            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[right.length()];
    }

    private record Match(SupportedTheme theme, int distance) {
    }
}
