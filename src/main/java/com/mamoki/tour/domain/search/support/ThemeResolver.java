package com.mamoki.tour.domain.search.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.mamoki.tour.domain.search.entity.ThemeDefinition;
import com.mamoki.tour.domain.search.entity.ThemeSynonym;
import com.mamoki.tour.domain.search.enums.SupportedTheme;
import com.mamoki.tour.domain.search.repository.ThemeDefinitionRepository;
import com.mamoki.tour.domain.search.repository.ThemeSynonymRepository;

import jakarta.annotation.PostConstruct;

/**
 * 검색어를 지원 테마로 잇는다.
 *
 * <p>정규화한 뒤 동의어와 정확히 맞는지 먼저 보고, 그다음 한 글자 차이까지만 오타로 본다.
 * 두 글자 이상 다르면 다른 말로 취급한다. 넉넉하게 잡을수록 엉뚱한 검색어가 지원 테마로
 * 둔갑하고, 그 순간 지원 테마라는 표시가 보증하는 것이 없어진다.
 *
 * <p>테마 목록은 {@code supported_theme} · {@code supported_theme_synonym} 에서
 * <b>기동 시 한 번</b> 읽어 메모리에 둔다(#54). 테마는 요청마다 조회할 값이 아니다.
 * {@code data.sql} 시드가 {@link DependsOnDatabaseInitialization} 덕분에 이 적재보다
 * 먼저 끝난다.
 *
 * <p>테이블이 비어 있으면 지원 테마가 없는 상태로 동작한다. 오류가 아니라 모든 검색이
 * 일반 검색으로 떨어지는 것이며, 코드에 남은 옛 목록으로 대신하지 않는다.
 */
@Component
@DependsOnDatabaseInitialization
public class ThemeResolver {

    /** 오타로 인정할 최대 편집 거리. */
    private static final int MAX_TYPO_DISTANCE = 1;

    /** 한 글자만 남는 말은 오타 판정에서 뺀다. 짧을수록 다른 말과 우연히 겹친다. */
    private static final int MIN_TYPO_LENGTH = 2;

    /** 제안으로 내보낼 최대 거리. 이보다 멀면 가까운 테마가 없다고 본다. */
    private static final int MAX_SUGGESTION_DISTANCE = 3;

    private static final int MAX_SUGGESTIONS = 3;

    private static final Logger log = LoggerFactory.getLogger(ThemeResolver.class);

    private final ThemeDefinitionRepository definitionRepository;
    private final ThemeSynonymRepository synonymRepository;

    /** 적재된 테마. 통째로 갈아 끼우므로 읽는 쪽은 잠금 없이 본다. */
    private volatile List<ThemeEntry> themes = List.of();

    /** 생성자가 둘이라 어느 쪽으로 주입할지 표시한다. */
    @Autowired
    public ThemeResolver(ThemeDefinitionRepository definitionRepository,
                         ThemeSynonymRepository synonymRepository) {

        this.definitionRepository = definitionRepository;
        this.synonymRepository = synonymRepository;
    }

    private ThemeResolver(List<ThemeEntry> themes) {
        this.definitionRepository = null;
        this.synonymRepository = null;
        this.themes = List.copyOf(themes);
    }

    /** DB 없이 고정 목록으로 쓰는 해석기. 순수 규칙만 확인하는 테스트용이다. */
    public static ThemeResolver withThemes(List<ThemeEntry> themes) {
        return new ThemeResolver(themes);
    }

    /** 기동 시 한 번, 그리고 시드를 다시 읽어야 할 때 호출한다. */
    @PostConstruct
    public void load() {
        if (definitionRepository == null || synonymRepository == null) {
            return;
        }

        themes = read();
        log.info("지원 테마 {}개를 적재했습니다.", themes.size());
    }

    /** 적재된 테마. 시드가 비어 있으면 빈 목록이다. */
    public List<ThemeEntry> themes() {
        return themes;
    }

    /** 공백과 대소문자를 지운다. 지운 결과가 비면 null 이다. */
    public static String normalize(String query) {
        if (query == null) {
            return null;
        }

        String normalized = query.replaceAll("\\s+", "").toLowerCase(Locale.KOREAN);
        return normalized.isEmpty() ? null : normalized;
    }

    /** 정규화한 검색어가 지원 테마의 동의어와 같거나 한 글자 차이면 그 테마로 본다. */
    public Optional<ThemeEntry> match(String query) {
        String normalized = normalize(query);

        if (normalized == null) {
            return Optional.empty();
        }

        for (ThemeEntry theme : themes) {
            if (theme.aliases().contains(normalized)) {
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
    public List<ThemeEntry> suggest(String query) {
        String normalized = normalize(query);

        if (normalized == null) {
            return List.of();
        }

        return matches(normalized)
                .filter(match -> match.distance() <= MAX_SUGGESTION_DISTANCE)
                .sorted(Comparator.comparingInt(Match::distance).thenComparingInt(Match::order))
                .limit(MAX_SUGGESTIONS)
                .map(Match::theme)
                .toList();
    }

    private Optional<Match> nearest(String normalized) {
        return matches(normalized)
                .min(Comparator.comparingInt(Match::distance).thenComparingInt(Match::order));
    }

    /** 적재 순서를 함께 담는다. 거리가 같으면 시드가 정한 순서로 가른다. */
    private java.util.stream.Stream<Match> matches(String normalized) {
        List<ThemeEntry> loaded = themes;

        return java.util.stream.IntStream.range(0, loaded.size())
                .mapToObj(index -> new Match(
                        loaded.get(index), bestDistance(loaded.get(index), normalized), index));
    }

    /** 동의어가 없는 테마는 어떤 검색어와도 이어지지 않는다. 가장 먼 거리로 둔다. */
    private static int bestDistance(ThemeEntry theme, String normalized) {
        return theme.aliases().stream()
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

    /**
     * 시드를 읽어 메모리에 올릴 목록을 만든다.
     *
     * <p>모르는 코드, 공급자 검색어 없는 테마, 동의어 없는 테마, 자격 토큰 없는 테마는
     * 조용히 넘기지 않고 기록에 남긴다. 모두 시드를 고치다 생기는 실수이고, 증상은
     * "검색이 그냥 안 걸린다" 라서 기록이 없으면 몇 주 뒤에야 드러난다.
     */
    private List<ThemeEntry> read() {
        // 적재 순서를 고정한다. 같은 시드에서 늘 같은 목록이 나와야 동의어 충돌 판정도
        // 기동할 때마다 달라지지 않는다.
        Map<String, List<ThemeSynonym>> synonymsByCode = synonymRepository
                .findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .collect(Collectors.groupingBy(ThemeSynonym::getThemeCode));

        List<ThemeEntry> loaded = new ArrayList<>();
        Map<String, SupportedTheme> claimedAliases = new HashMap<>();

        for (ThemeDefinition definition : definitionRepository
                .findAllByActiveTrueOrderBySortOrderAscCodeAsc()) {

            Optional<SupportedTheme> code = SupportedTheme.from(definition.getCode());

            if (code.isEmpty()) {
                log.warn("알 수 없는 지원 테마 코드라 건너뜁니다. code={}", definition.getCode());
                continue;
            }

            List<String> keywords = split(definition.getSearchKeywords());

            if (keywords.isEmpty()) {
                // 공급자에게 보낼 말이 없으면 조회 자체를 못 한다. 그대로 두면 "부를 것이
                // 없었다" 가 "공급자가 답하지 않았다"(NO_DATA) 로 보고된다. 목록에서 뺀다.
                log.warn("공급자 검색어가 없어 지원 테마로 쓸 수 없습니다. code={}", definition.getCode());
                continue;
            }

            List<String> aliases = aliasesOf(
                    synonymsByCode.getOrDefault(definition.getCode(), List.of()),
                    code.get(), claimedAliases);
            List<String> matchTokens = split(definition.getMatchTokens());

            if (aliases.isEmpty()) {
                log.warn("동의어가 없어 어떤 검색어로도 이어지지 않습니다. code={}", definition.getCode());
            }

            if (matchTokens.isEmpty()) {
                log.warn("자격 토큰이 없어 어떤 장소도 이 테마의 결과가 되지 못합니다. code={}",
                        definition.getCode());
            }

            loaded.add(new ThemeEntry(code.get(), definition.getDisplayName(), aliases,
                    keywords, matchTokens));
        }

        return List.copyOf(loaded);
    }

    /**
     * 동의어를 정규화해 모은다.
     *
     * <p>같은 말이 두 테마를 가리키면 앞선 테마가 가져가고 뒤는 버린다. DB 유니크 제약이
     * 먼저 막지만, 제약을 우회해 들어온 데이터에서도 결과가 적재 순서에 따라 달라지지
     * 않도록 여기서 한 번 더 결정한다.
     */
    private static List<String> aliasesOf(List<ThemeSynonym> synonyms, SupportedTheme code,
                                          Map<String, SupportedTheme> claimed) {

        List<String> aliases = new ArrayList<>();

        for (ThemeSynonym synonym : synonyms) {
            String normalized = normalize(synonym.getSynonym());

            if (normalized == null) {
                continue;
            }

            SupportedTheme owner = claimed.putIfAbsent(normalized, code);

            if (owner == null) {
                aliases.add(normalized);
            } else if (owner != code) {
                log.warn("같은 동의어가 두 테마를 가리킵니다. synonym={} 사용={} 무시={}",
                        normalized, owner, code);
            }
        }

        return List.copyOf(aliases);
    }

    /** 쉼표로 구분한 값을 목록으로 푼다. 빈 조각은 버린다. */
    private static List<String> split(String value) {
        if (value == null) {
            return List.of();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .distinct()
                .toList();
    }

    /** @param order 시드가 정한 순서. 거리가 같은 테마끼리 가른다. */
    private record Match(ThemeEntry theme, int distance, int order) {
    }
}
