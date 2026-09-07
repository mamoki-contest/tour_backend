package com.mamoki.tour.domain.attraction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주요관광지점 입장객 통계 보조 근거.
 *
 * <p>등록 관광지의 실제 집계다. 미등록·미입력 장소를 0명으로 만들지 않는다.
 *
 * <p>공식 파일 임포터가 아직 없어 현재는 항상 미등록 상태로 내려간다. 값이 없다는 사실을
 * 상태로 알리며, 프론트가 계약을 먼저 맞출 수 있도록 필드는 제공한다.
 */
@Schema(description = "주요관광지점 입장객 통계. 등록 관광지에만 있으며 미등록을 0명으로 만들지 않는다.")
public record VisitorStatsView(

        @Schema(description = "AVAILABLE=집계 있음, NOT_REGISTERED=통계 미등록, NOT_IMPORTED=아직 적재하지 않음")
        Status status,

        @Schema(description = "최근 공표 월 입장객 수. 없으면 null")
        Long count,

        @Schema(description = "공표 기간", example = "202607")
        String period,

        @Schema(description = "PROVISIONAL=잠정, CONFIRMED=확정. 값이 없으면 null")
        String countStatus
) {

    public enum Status {
        AVAILABLE,
        NOT_REGISTERED,
        NOT_IMPORTED
    }

    /** 입장객 통계 임포터가 아직 없어 값을 알 수 없는 상태. */
    public static VisitorStatsView notImported() {
        return new VisitorStatsView(Status.NOT_IMPORTED, null, null, null);
    }
}
