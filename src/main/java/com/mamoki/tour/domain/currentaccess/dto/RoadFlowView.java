package com.mamoki.tour.domain.currentaccess.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mamoki.tour.global.enums.DataStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관광지 주변 도로의 현재 소통.
 *
 * <p>속도를 원본 그대로 담는다. 혼잡 등급으로 바꾸지 않는다. 제한속도를 알 수 없어 25km/h 가
 * 막힌 것인지 원래 시내 도로인지 우리가 판단할 수 없기 때문이다. 판단할 근거가 없는 등급을
 * 만들면 "여기는 막히는 곳" 이라는 잘못된 단정이 된다.
 *
 * <p>같은 이유로 <b>서로 다른 관광지의 평균 속도를 모아 접근성 순위로 쓰면 안 된다.</b>
 * 도심 관광지는 늘 느리고 외곽은 늘 빠르다. 그것은 혼잡이 아니라 입지다.
 *
 * @param roads       도로명별 요약. 이름 없는 구간은 담지 않는다.
 * @param observedAt  공급자가 알려준 관측 생성시각. 5분 단위로 갱신된다.
 */
@Schema(description = """
        관광지 주변 도로의 현재 소통. 속도는 원본 값이며 혼잡 등급이 아닙니다.
        다른 관광지와 비교해 접근성 순위로 쓰지 마세요.""")
public record RoadFlowView(

        @Schema(description = "AVAILABLE=관측 있음, NO_DATA=정보 없음")
        DataStatus status,

        @Schema(description = "관측된 도로 구간 수", example = "44")
        Integer linkCount,

        @Schema(description = """
                관측 구간의 평균 통행 속도(km/h). 제한속도 대비가 아니라 원본 평균입니다.
                도심은 낮고 외곽은 높게 나오며, 그 차이는 혼잡이 아니라 입지입니다""",
                example = "38.5")
        Double averageSpeed,

        @Schema(description = "도로명별 요약. 관측 구간이 많은 순")
        List<RoadSegmentView> roads,

        @Schema(description = "공급자가 알려준 관측 생성시각. 정보 없음이면 null")
        LocalDateTime observedAt
) {

    public static RoadFlowView noData() {
        return new RoadFlowView(DataStatus.NO_DATA, null, null, List.of(), null);
    }

    /**
     * 도로 하나의 요약.
     *
     * @param averageSpeed      그 도로 구간들의 평균 속도(km/h)
     * @param averageTravelTime 그 도로 구간들의 평균 통행시간(초)
     */
    @Schema(description = "도로명별 현재 소통")
    public record RoadSegmentView(

            @Schema(description = "도로명", example = "경포로")
            String roadName,

            @Schema(description = "관측 구간 수", example = "12")
            int linkCount,

            @Schema(description = "평균 통행 속도(km/h)", example = "40.2")
            double averageSpeed,

            @Schema(description = "평균 통행시간(초)", example = "10.4")
            double averageTravelTime
    ) {
    }
}
