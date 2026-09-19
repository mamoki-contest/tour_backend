package com.mamoki.tour.domain.placemapping.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;

/**
 * 카탈로그 대상이 아닌 장소를 가려내는 사전.
 *
 * <p>사전을 코드 상수가 아니라 리소스 파일에 둔다. 실제 데이터를 보고 자주 고칠 규칙이고,
 * 무엇이 바뀌었는지가 파일 하나의 diff 로 남아야 한다.
 *
 * <h2>두 갈래가 모두 맞아야 한다</h2>
 * 카카오 카테고리와 이름 접미어가 둘 다 맞을 때만 선다. 한쪽만이면 판정하지 않는다.
 * 카테고리만 보면 카카오가 대표로 고른 장소가 원천 이름과 다를 때 엉뚱한 종류로 읽히고,
 * 이름만 보면 {@code 벨라스톤CC} 와 {@code 스톤CC펜션} 을 가르지 못한다.
 *
 * <h2>카탈로그가 담는 종류는 규칙을 끈다</h2>
 * 접미어 규칙은 강원 카탈로그에 그 말이 든 장소가 하나라도 있으면 그 실행에서 꺼진다.
 * 카탈로그가 담고 있는 종류를 "카탈로그 대상이 아니다" 라고 말할 수는 없다. 실제로
 * KorService2 는 골프장·리조트·휴게소·영화관을 레포츠·숙박·문화시설로 담고 있다. 그것을
 * 분모에서 빼면 매칭률은 올라가지만 올라간 만큼이 거짓이 된다.
 *
 * <p>규칙이 꺼졌다는 사실은 {@link #suppressedSuffixes()} 로 배치 로그에 남긴다. 조용히
 * 꺼지면 사전을 고쳐도 아무 일이 일어나지 않는 이유를 아무도 모른다.
 */
public final class OutOfCatalogRules {

    private static final String RESOURCE_PATH = "/place-mapping/out-of-catalog-rules.txt";

    private static final String SECTION_CATEGORY_GROUP = "category-group";
    private static final String SECTION_CATEGORY_NAME = "category-name";
    private static final String SECTION_NAME_SUFFIX = "name-suffix";

    private final Set<String> categoryGroupCodes;
    private final List<String> categoryNameFragments;

    /** 접미어 규칙 원문 → 비교에 쓰는 정규화 형태. 근거로 남길 때 원문을 적는다. */
    private final Map<String, String> nameSuffixes;

    private final List<String> suppressedSuffixes;

    private OutOfCatalogRules(Set<String> categoryGroupCodes,
                              List<String> categoryNameFragments,
                              Map<String, String> nameSuffixes,
                              List<String> suppressedSuffixes) {
        this.categoryGroupCodes = categoryGroupCodes;
        this.categoryNameFragments = categoryNameFragments;
        this.nameSuffixes = nameSuffixes;
        this.suppressedSuffixes = suppressedSuffixes;
    }

    /** 저장소에 둔 사전을 읽는다. 파일이 없거나 읽히지 않으면 기동을 멈춘다. */
    public static OutOfCatalogRules load() {
        try (InputStream stream = OutOfCatalogRules.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "카탈로그 제외 사전을 찾지 못했습니다: " + RESOURCE_PATH);
            }

            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("카탈로그 제외 사전을 읽지 못했습니다: " + RESOURCE_PATH, e);
        }
    }

    /**
     * 사전 본문을 읽는다.
     *
     * <p>{@code [구획]} 으로 나누고 한 줄에 규칙 하나다. {@code #} 로 시작하는 줄과 빈 줄은
     * 건너뛴다. 줄 안의 {@code #} 는 자르지 않는다 — 규칙에 들어갈 수 있는 글자다.
     */
    public static OutOfCatalogRules parse(String text) {
        Set<String> groupCodes = new LinkedHashSet<>();
        List<String> categoryNames = new java.util.ArrayList<>();
        Map<String, String> suffixes = new LinkedHashMap<>();

        String section = null;

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.strip();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).strip();
                continue;
            }

            switch (section == null ? "" : section) {
                case SECTION_CATEGORY_GROUP -> groupCodes.add(line.toUpperCase());
                case SECTION_CATEGORY_NAME -> categoryNames.add(line);
                case SECTION_NAME_SUFFIX -> {
                    String normalized = PlaceNameNormalizer.normalize(line);

                    if (normalized != null) {
                        suffixes.put(line, normalized);
                    }
                }
                default -> throw new IllegalStateException(
                        "사전에 구획 밖의 줄이 있습니다: " + line);
            }
        }

        return new OutOfCatalogRules(Set.copyOf(groupCodes), List.copyOf(categoryNames),
                Map.copyOf(suffixes), List.of());
    }

    /**
     * 카탈로그에 실재하는 종류의 접미어 규칙을 끈다.
     *
     * @param catalogNames 강원 카탈로그의 관광지명 전부. 시·군을 가리지 않는다. 어느 시·군에
     *                     하나라도 있으면 그 종류는 카탈로그가 담는 것이다.
     */
    public OutOfCatalogRules withCatalogEvidence(Collection<String> catalogNames) {
        Set<String> normalizedCatalog = new LinkedHashSet<>();

        for (String name : catalogNames) {
            String normalized = PlaceNameNormalizer.normalize(name);

            if (normalized != null) {
                normalizedCatalog.add(normalized);
            }
        }

        Map<String, String> remaining = new LinkedHashMap<>();
        List<String> suppressed = new java.util.ArrayList<>();

        nameSuffixes.forEach((rule, normalizedRule) -> {
            boolean inCatalog = normalizedCatalog.stream()
                    .anyMatch(name -> name.contains(normalizedRule));

            if (inCatalog) {
                suppressed.add(rule);
            } else {
                remaining.put(rule, normalizedRule);
            }
        });

        return new OutOfCatalogRules(categoryGroupCodes, categoryNameFragments,
                Map.copyOf(remaining), List.copyOf(suppressed));
    }

    /** 카탈로그에 실재해 이번 실행에서 끈 접미어 규칙들. 배치 로그가 그대로 적는다. */
    public List<String> suppressedSuffixes() {
        return suppressedSuffixes;
    }

    public int categoryRuleCount() {
        return categoryGroupCodes.size() + categoryNameFragments.size();
    }

    public int nameSuffixRuleCount() {
        return nameSuffixes.size();
    }

    /**
     * @param sourceName        원천이 적은 이름
     * @param categoryGroupCode 카카오 {@code category_group_code}. 비어 오는 장소가 많다.
     * @param categoryName      카카오 {@code category_name}
     * @return 두 갈래가 모두 맞았을 때의 근거. 하나라도 어긋나면 빈 값이다.
     */
    public Optional<OutOfCatalogEvidence> evaluate(String sourceName,
                                                   String categoryGroupCode, String categoryName) {
        String categoryRule = matchedCategoryRule(categoryGroupCode, categoryName);

        if (categoryRule == null) {
            return Optional.empty();
        }

        String suffixRule = matchedSuffixRule(sourceName);

        return suffixRule == null
                ? Optional.empty()
                : Optional.of(new OutOfCatalogEvidence(categoryRule, suffixRule));
    }

    private String matchedCategoryRule(String categoryGroupCode, String categoryName) {
        if (categoryGroupCode != null && categoryGroupCodes.contains(categoryGroupCode.strip().toUpperCase())) {
            return categoryGroupCode.strip().toUpperCase();
        }

        if (categoryName == null) {
            return null;
        }

        for (String fragment : categoryNameFragments) {
            if (categoryName.contains(fragment)) {
                return fragment;
            }
        }

        return null;
    }

    private String matchedSuffixRule(String sourceName) {
        String normalized = PlaceNameNormalizer.normalize(sourceName);

        if (normalized == null) {
            return null;
        }

        for (Map.Entry<String, String> entry : nameSuffixes.entrySet()) {
            if (normalized.endsWith(entry.getValue()) && !normalized.equals(entry.getValue())) {
                return entry.getKey();
            }
        }

        return null;
    }
}
