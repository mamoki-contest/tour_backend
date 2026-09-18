package com.mamoki.tour.domain.attraction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주요관광지점 입장객 통계 보조 근거.
 *
 * <p>등록 관광지의 실제 집계다. 미등록·미입력 장소를 0명으로 만들지 않는다.
 *
 * <p>관광지식정보시스템 공식 엑셀을 적재해 채운다. 적재 전에는 {@code NOT_IMPORTED} 이고,
 * 적재 후에도 공표월에 집계가 없던 장소는 {@code NOT_REGISTERED} 로 남는다. 어느 쪽이든
 * 0 명으로 바꾸지 않는다.
 */
@Schema(description = "주요관광지점 입장객 통계. 등록 관광지에만 있으며 미등록을 0명으로 만들지 않는다.")
public record VisitorStatsView(

        @Schema(description = """
                AVAILABLE=공표월 집계 있음,
                NOT_REGISTERED=통계에 없거나 공표월에 집계되지 않음,
                NOT_IMPORTED=아직 적재하지 않음""")
        Status status,

        @Schema(description = "공표월 입장객 수. 내국인과 외국인을 더한 공식 합계다. 없으면 null")
        Long count,

        @Schema(description = "공표월", example = "202603")
        String period,

        @Schema(description = """
                PROVISIONAL=잠정, CONFIRMED=확정. 값이 없으면 null.
                확정치는 이듬해 4월에 공표됩니다""")
        String countStatus
) {

    public enum Status {
        AVAILABLE,
        NOT_REGISTERED,
        NOT_IMPORTED
    }

    /** 아직 공식 파일을 적재하지 않아 값을 알 수 없는 상태. */
    public static VisitorStatsView notImported() {
        return new VisitorStatsView(Status.NOT_IMPORTED, null, null, null);
    }
}
