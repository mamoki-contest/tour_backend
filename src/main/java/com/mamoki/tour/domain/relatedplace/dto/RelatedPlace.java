package com.mamoki.tour.domain.relatedplace.dto;

import com.mamoki.tour.domain.relatedplace.enums.RelatedPlaceKind;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 연관 장소 한 곳.
 *
 * <p>유형과 대체지 후보 자격을 항상 함께 내려준다. 어느 묶음에 담겨 있는지로만 유형을
 * 짐작하게 하면, 프론트가 묶음 구성을 바꿀 때 의미가 조용히 어긋난다.
 *
 * @param eligibleAsAlternative 대체지 후보 자격 충족 여부. 음식점·숙박시설은 항상 false 다.
 * @param visitTiming           대체지 후보 자격의 근거인 날짜 탐색 결과.
 *                              관광지가 아니어서 자격을 따지지 않았으면 null 이다.
 */
@Schema(description = "연관 장소. 유형과 대체지 후보 자격을 함께 담습니다.")
public record RelatedPlace(

        @Schema(description = "연관 장소명", example = "주문진수산시장")
        String name,

        @Schema(description = "ATTRACTION=관광지, RESTAURANT=음식점, LODGING=숙박시설, OTHER=공급자 대분류 미상")
        RelatedPlaceKind kind,

        @Schema(description = "공급자 대분류 원본", example = "관광지")
        String categoryLarge,

        @Schema(description = "공급자 중분류 원본. 없으면 null", example = "쇼핑")
        String categoryMiddle,

        @Schema(description = "공급자 소분류 원본. 없으면 null", example = "시장")
        String categorySmall,

        @Schema(description = "연관 장소의 법정동 시·군 코드 5자리. 기준 관광지와 다를 수 있습니다.", example = "51150")
        String lawdCode,

        @Schema(description = "연관 장소의 시·군 이름. 없으면 null", example = "강릉시")
        String regionName,

        @Schema(description = "공급자가 매긴 연관 순위. 1이 가장 가깝습니다. 값이 없으면 null 이며 0 으로 해석하면 안 됩니다.")
        Integer rank,

        @Schema(description = """
                대체지 후보 자격 충족 여부.
                관광지이면서, 원래 장소와 다른 곳이고, 유효한 방문 혼잡도 예측을 가진 경우에만 true 입니다.""")
        boolean eligibleAsAlternative,

        @Schema(description = "자격 판단의 근거인 날짜 탐색 결과. 관광지가 아니면 null")
        VisitTiming visitTiming
) {
}
