package com.mamoki.tour.domain.search.enums;

/**
 * 검색 결과의 성격.
 *
 * <p>두 유형은 신뢰 수준이 다르다. 화면에서 같은 문구로 표시하면 검증된 추천과
 * 단순 검색 결과를 구분할 수 없게 된다.
 */
public enum SearchResultType {

    /** 지원 테마. 추천 자격을 적용한 결과다. */
    SUPPORTED_THEME,

    /** 일반 검색. 공급자 키워드 검색 결과 그대로이며 추천 보증이 없다. */
    GENERAL_SEARCH
}
