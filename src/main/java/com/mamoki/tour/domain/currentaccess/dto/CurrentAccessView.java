package com.mamoki.tour.domain.currentaccess.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 현재 접근 혼잡.
 *
 * <p>지금 그 관광지까지 가는 길이 어떤지를 담는다. <b>방문 혼잡도 예측(visitTiming)과 전혀 다른
 * 신호다.</b> 저쪽은 앞으로 30일의 장소 안 붐빔이고, 이쪽은 지금 이 순간의 도로와 주차다.
 * 둘을 합치거나 서로를 추정하지 않는다.
 *
 * <p>관광지 안에 사람이 얼마나 있는지는 알 수 없다. 도로가 막힌다고 관광지가 붐비는 것도,
 * 도로가 한산하다고 관광지가 비어 있는 것도 아니다.
 *
 * @param checkedAt 우리가 조회한 시각. 공급자의 관측 생성시각과 다르다.
 */
@Schema(description = """
        현재 접근 혼잡. 지금 그 관광지까지 가는 길의 도로·주차 여건입니다.
        미래 방문 혼잡도 예측이나 관광지 내부 인파와는 다른 신호입니다.""")
public record CurrentAccessView(

        @Schema(description = "주변 도로의 현재 소통")
        RoadFlowView road,

        @Schema(description = "주변 주차 여건")
        ParkingView parking,

        @Schema(description = "조회 시각. 공급자의 관측 생성시각과 구분합니다")
        LocalDateTime checkedAt,

        @Schema(description = "데이터 출처", example = "국가교통정보센터")
        String source
) {
}
