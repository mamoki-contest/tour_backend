package com.mamoki.tour.domain.search.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.search.enums.SearchResultType;
import com.mamoki.tour.global.enums.DataStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 검색 응답.
 *
 * <p>0건과 정보 없음을 구분한다. {@code dataStatus} 가 AVAILABLE 인데 items 가 비었으면
 * 조건에 맞는 장소가 없다는 뜻이고, NO_DATA 이면 공급자에게서 답을 얻지 못했다는 뜻이다.
 *
 * @param resultType      SUPPORTED_THEME 만 추천 자격을 적용받은 결과다.
 * @param appliedTheme    이어진 지원 테마. 일반 검색이면 null.
 * @param appliedQuery    실제로 공급자에게 보낸 검색어. 정규화 결과를 확인할 수 있게 남긴다.
 * @param suggestedThemes 결과가 없을 때 대신 보여줄 가까운 테마. 없으면 빈 목록.
 */
@Schema(description = "검색 응답. 검증된 테마 추천과 일반 검색 결과를 구분합니다.")
public record AttractionSearchResponse(

        @Schema(description = """
                결과 유형.
                SUPPORTED_THEME=추천 자격을 적용한 지원 테마 결과, GENERAL_SEARCH=일반 검색 결과""")
        SearchResultType resultType,

        @Schema(description = "이어진 지원 테마. 일반 검색이면 null")
        ThemeView appliedTheme,

        @Schema(description = "공급자에게 보낸 검색어", example = "해수욕장")
        String appliedQuery,

        @Schema(description = "검색된 관광지 목록")
        List<AttractionResponse> items,

        @Schema(description = "조건에 해당하는 전체 건수", example = "20")
        int totalCount,

        @Schema(description = "요청한 페이지 번호", example = "1")
        int page,

        @Schema(description = "요청한 조회 개수", example = "20")
        int size,

        @Schema(description = """
                결과가 없을 때 제안하는 가까운 지원 테마.
                검색 결과를 대신하는 값이 아니며, 가까운 테마가 없으면 빈 목록입니다""")
        List<ThemeView> suggestedThemes,

        @Schema(description = """
                데이터 상태.
                AVAILABLE=유효, STALE=갱신 실패로 최종 정상 데이터 사용, NO_DATA=정보 없음.
                AVAILABLE 이면서 items 가 비어 있으면 조건에 맞는 장소가 없다는 뜻입니다""")
        DataStatus dataStatus,

        @Schema(description = "응답에 사용한 데이터의 수집 시각. NO_DATA 이면 null")
        LocalDateTime collectedAt,

        @Schema(description = "데이터 출처", example = "KorService2")
        String source
) {
}
