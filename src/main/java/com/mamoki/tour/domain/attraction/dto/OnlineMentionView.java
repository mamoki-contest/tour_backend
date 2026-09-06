package com.mamoki.tour.domain.attraction.dto;

import java.time.LocalDateTime;

import com.mamoki.tour.global.enums.MentionStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관광지 한 곳의 온라인 언급량 표시값.
 *
 * @param count       블로그 검색 결과 수. 정상 수집이 아니면 null 이며 0 으로 채우지 않는다.
 * @param ruleVersion 이 값을 만든 검색어 규칙. 규칙이 다른 값끼리 비교하면 안 된다.
 */
@Schema(description = "온라인 언급량. 블로그 검색 결과 수이며 실제 방문객 수·검색량·현재 혼잡이 아니다.")
public record OnlineMentionView(

        @Schema(description = "COLLECTED=정상, AMBIGUOUS=이름이 모호함, UNAVAILABLE=대상 아님, COLLECTION_FAILED=수집 실패")
        MentionStatus status,

        @Schema(description = "블로그 검색 결과 수. 정상 수집이 아니면 null", example = "140006")
        Long count,

        @Schema(description = "이 값을 수집한 시각")
        LocalDateTime collectedAt,

        @Schema(description = "검색어 규칙 버전", example = "name+sigungu+여행")
        String ruleVersion
) {

    /** 활성 스냅샷에 이 관광지가 없을 때. 언급이 적다는 뜻이 아니다. */
    public static OnlineMentionView notCollected(String ruleVersion) {
        return new OnlineMentionView(MentionStatus.COLLECTION_FAILED, null, null, ruleVersion);
    }

    public boolean isSortable() {
        return status == MentionStatus.COLLECTED && count != null;
    }
}
