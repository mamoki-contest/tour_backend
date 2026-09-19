package com.mamoki.tour.domain.placemapping.enums;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 매핑이 이어 주는 원천.
 *
 * <p>원천마다 이름의 성격과 매칭 범위가 다르다. 한 이름이 여러 원천에 나타나도 원천별로
 * 따로 판정한다. TMAP 파일의 {@code 강릉 경포해변} 과 입장객통계의 {@code 경포해수욕장} 은
 * 다른 문자열이고, 어느 쪽이 어떻게 이어졌는지를 원천별로 되짚을 수 있어야 한다.
 */
public enum MappingSource {

    /**
     * TMAP 검색순위 CSV 의 관광지명.
     *
     * <p>매칭 범위가 이름뿐이다. 파일의 지역명 표기가 시드의 시·군명과 어긋날 수 있어
     * 적재 때부터 이름만으로 이어 왔고, 매핑도 같은 범위를 쓴다. 범위를 좁히면 지금
     * 붙어 있던 행이 말없이 떨어져 나간다.
     */
    TMAP("tmap", MatchScope.NAME_ONLY),

    /**
     * 주요관광지점 입장객통계 엑셀의 관광지명.
     *
     * <p>매칭 범위가 (시·군, 이름) 이다. 공식 파일은 이 조합이 유일하고, 이름만으로 이으면
     * 같은 이름이 여러 시·군에 있을 때 엉뚱한 곳에 입장객 수가 붙는다(#37).
     */
    VISITOR_STATS("visitor-stats", MatchScope.REGION_AND_NAME),

    /**
     * 연관 장소 공급자({@code TarRlteTarService1}) 의 기준 관광지명.
     *
     * <p>조회가 시·군 단위라 범위가 (시·군, 이름) 이다. 다른 둘과 달리 조회 시점에 쓰이므로
     * 매핑을 넣으면 재적재 없이 바로 반영된다.
     */
    RELATED_PLACE("related", MatchScope.REGION_AND_NAME);

    /** 원천 이름을 카탈로그에서 찾을 때 좁히는 범위. */
    public enum MatchScope {

        /** 이름만으로 찾는다. */
        NAME_ONLY,

        /** 같은 시·군 안에서 이름으로 찾는다. */
        REGION_AND_NAME
    }

    private final String optionValue;
    private final MatchScope matchScope;

    MappingSource(String optionValue, MatchScope matchScope) {
        this.optionValue = optionValue;
        this.matchScope = matchScope;
    }

    /** 커맨드라인 {@code --source} 에 쓰는 값. 상수 이름을 바꿔도 운영자 명령이 깨지지 않게 따로 둔다. */
    public String optionValue() {
        return optionValue;
    }

    public MatchScope matchScope() {
        return matchScope;
    }

    public static Optional<MappingSource> from(String optionValue) {
        return Arrays.stream(values())
                .filter(source -> source.optionValue.equalsIgnoreCase(optionValue))
                .findFirst();
    }

    public static String optionValues() {
        return Arrays.stream(values())
                .map(MappingSource::optionValue)
                .collect(Collectors.joining(", "));
    }
}
