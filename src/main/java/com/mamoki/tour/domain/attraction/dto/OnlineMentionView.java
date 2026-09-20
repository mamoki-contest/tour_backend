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
@Schema(description = """
        온라인 언급량. 블로그 검색 결과 수이며 실제 방문객 수·검색량·현재 혼잡이 아니다.
        정상 수집(COLLECTED)이 아니면 count 가 null 이며 정렬 대상에서 빠진다.
        빠진 이유는 status 로 구분한다 — 같은 문구로 표시하지 마세요.""")
public record OnlineMentionView(

        @Schema(description = """
                COLLECTED=정상 수집(count 유효, 0건도 정상),
                AMBIGUOUS=이름이 모호해 그 장소의 값으로 볼 수 없음,
                UNAVAILABLE=검색어를 만들 수 없어 수집 대상이 아님,
                COLLECTION_FAILED=값을 얻지 못함(스냅샷에 이 장소가 없는 경우 포함)""")
        MentionStatus status,

        @Schema(description = "블로그 검색 결과 수. 정상 수집이 아니면 null", example = "140006")
        Long count,

        @Schema(description = "이 값을 수집한 시각")
        LocalDateTime collectedAt,

        @Schema(description = "검색어 규칙 버전", example = "name+sigungu+여행")
        String ruleVersion
) {

    /**
     * 활성 스냅샷에 이 관광지가 없을 때. 언급이 적다는 뜻이 아니다.
     *
     * <p>스냅샷에 <b>있는</b> 장소는 이 자리로 오지 않는다. 그쪽은 수집이 내린 판정이
     * 이미 있으므로 {@link #of} 로 그 상태를 그대로 옮긴다(#94).
     */
    public static OnlineMentionView notCollected(String ruleVersion) {
        return new OnlineMentionView(MentionStatus.COLLECTION_FAILED, null, null, ruleVersion);
    }

    /**
     * 스냅샷에 담긴 항목의 판정을 그대로 옮긴다.
     *
     * <p>정상 수집이 아닌 항목에는 수치를 싣지 않는다. 모호 판정 중에는 수치가 남아 있는
     * 갈래가 있는데(TMAP 에 수록됐는데 0건), 그 0 을 그대로 내보내면 정렬에 쓰지 않더라도
     * 화면에는 "언급 0건" 으로 보인다. 값을 그 장소의 언급량으로 볼 수 없다고 판정한 것이
     * 모호이므로 수치도 함께 내리지 않는다.
     */
    public static OnlineMentionView of(MentionStatus status, Long count,
                                       LocalDateTime collectedAt, String ruleVersion) {

        return new OnlineMentionView(status,
                status == MentionStatus.COLLECTED ? count : null,
                collectedAt, ruleVersion);
    }

    public boolean isSortable() {
        return status == MentionStatus.COLLECTED && count != null;
    }
}
