package com.mamoki.tour.domain.mention.support;

import org.springframework.stereotype.Component;

/**
 * 관광지 하나를 조회할 검색어를 만든다.
 *
 * <p>규칙이 바뀌면 같은 장소의 언급량도 달라진다. 그래서 스냅샷마다 어떤 규칙으로 수집했는지
 * 기록해야 하는데, 버전을 사람이 따로 적으면 규칙만 바꾸고 버전을 안 올리는 일이 생긴다.
 * 여기서는 버전을 규칙 자체에서 만들어 둘이 어긋날 수 없게 한다.
 *
 * <p>검색어는 AND 로 동작한다. 낱말을 더할수록 결과가 줄고, 장소 성격에 따라 줄어드는 폭이
 * 다르다. 예를 들어 사찰은 블로그에서 {@code 여행} 보다 다른 말과 함께 쓰여, {@code 여행} 을
 * 붙이면 실제 언급량이 아니라 여행 맥락의 언급량을 재게 된다.
 */
@Component
public class SearchQueryRule {

    private final SearchQueryRuleProperties properties;

    public SearchQueryRule(SearchQueryRuleProperties properties) {
        this.properties = properties;
    }

    /**
     * @param sigunguName 시·군명. 규칙이 시·군을 쓰지 않거나 값이 없으면 생략한다.
     * @return 검색어. 관광지명이 비어 있으면 null.
     */
    public String build(String placeName, String sigunguName) {
        String name = normalize(placeName);

        if (name == null) {
            return null;
        }

        StringBuilder query = new StringBuilder(name);

        if (properties.includeSigungu()) {
            String sigungu = normalize(sigunguName);

            if (sigungu != null) {
                query.append(' ').append(sigungu);
            }
        }

        String suffix = normalize(properties.suffix());
        if (suffix != null) {
            query.append(' ').append(suffix);
        }

        return query.toString();
    }

    /** 규칙에서 직접 만든 버전. 규칙을 바꾸면 버전도 함께 바뀐다. */
    public String version() {
        StringBuilder version = new StringBuilder("name");

        if (properties.includeSigungu()) {
            version.append("+sigungu");
        }

        String suffix = normalize(properties.suffix());
        if (suffix != null) {
            version.append("+").append(suffix);
        }

        return version.toString();
    }

    /** 연속 공백을 하나로 줄이고 앞뒤를 다듬는다. 같은 이름이 표기 차이로 갈라지지 않게 한다. */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.strip().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }
}
