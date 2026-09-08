package com.mamoki.tour.domain.attraction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mamoki.tour.domain.visittiming.dto.VisitTiming;

/**
 * 관광지 목록 항목의 표준 계약.
 *
 * <p>결측은 null 로 내려간다. 프론트는 null 을 `정보 없음`으로 표시하고 0 으로 해석하지 않는다.
 *
 * @param regionName 지역코드 매핑에서 찾은 시·군 이름. 매핑이 없으면 null.
 * @param centerRank 시·군 내부 중심관광지 순위. 매칭되지 않으면 null이며, 순위 없음이 낮은 순위를 뜻하지 않는다.
 * @param baseAt     이 항목의 공급자 기준 시점.
 * @param visitTiming 날짜 탐색 결과. 날짜 모드를 지정하지 않은 요청에서는 null.
 */
@Schema(description = "관광지 목록 항목. 결측은 모두 null 이며 0 으로 해석하면 안 됩니다.")
public record AttractionResponse(

        @Schema(description = "표준 관광지 식별자", example = "2513889")
        String contentId,

        @Schema(description = "관광지명", example = "국립산악박물관")
        String name,

        @Schema(description = "대표 이미지. 없으면 null")
        String imageUrl,

        @Schema(description = "주소. 없으면 null", example = "강원특별자치도 속초시 미시령로 3054")
        String address,

        @Schema(description = "위도. 좌표가 없으면 null", example = "38.2025955406")
        BigDecimal latitude,

        @Schema(description = "경도. 좌표가 없으면 null", example = "128.5404766211")
        BigDecimal longitude,

        @Schema(description = "관광지 분류", example = "14")
        String contentTypeId,

        @Schema(description = "법정동 시·군 코드 5자리", example = "51210")
        String lawdCode,

        @Schema(description = "시·군 이름. 지역코드 매핑이 없으면 null", example = "속초시")
        String regionName,

        @Schema(description = "시·군 내부 중심관광지 순위. 시·군을 지정하지 않았거나 매칭되지 않으면 null. 시·군 사이의 절대 순위가 아니다.")
        Integer centerRank,

        @Schema(description = "이 항목의 공급자 기준 시점. 서버 저장 시각이 아닙니다.")
        LocalDateTime baseAt,

        @Schema(description = "온라인 언급량. 목록 정렬의 주 지표")
        OnlineMentionView onlineMention,

        @Schema(description = "시·군 내 TMAP 검색순위. 보조 근거")
        TmapRankView tmapRank,

        @Schema(description = "주요관광지점 입장객 통계. 보조 근거")
        VisitorStatsView visitorStats,

        @Schema(description = """
                날짜 탐색 결과. dateMode 를 지정하지 않은 요청에서는 null 입니다.
                status 는 이 장소 자신의 30일 분포 안에서의 상대 수준이며,
                다른 관광지의 status 와 비교해 혼잡도 순위로 쓰면 안 됩니다.""")
        VisitTiming visitTiming
) {

    public static AttractionResponse of(AttractionSnapshot snapshot, String regionName) {
        return of(snapshot, regionName, null, null, null, null);
    }

    public static AttractionResponse of(AttractionSnapshot snapshot, String regionName, Integer centerRank) {
        return of(snapshot, regionName, centerRank, null, null, null);
    }

    public static AttractionResponse of(AttractionSnapshot snapshot, String regionName,
                                        Integer centerRank, OnlineMentionView onlineMention,
                                        TmapRankView tmapRank) {
        return of(snapshot, regionName, centerRank, onlineMention, tmapRank, null);
    }

    /**
     * 신호는 각각 독립 필드로 담는다. 값이 없으면 상태로 알리고 0 이나 낮은 순위로 채우지 않는다.
     *
     * <p>온라인 언급량·TMAP 순위·입장객 수·날짜 탐색은 서로 다른 데이터다. 하나의 점수로 합치지
     * 않으며, 어느 하나의 결측을 다른 신호로 추정하지도 않는다.
     */
    public static AttractionResponse of(AttractionSnapshot snapshot, String regionName,
                                        Integer centerRank, OnlineMentionView onlineMention,
                                        TmapRankView tmapRank, VisitTiming visitTiming) {
        return new AttractionResponse(
                snapshot.contentId(),
                snapshot.name(),
                snapshot.imageUrl(),
                snapshot.address(),
                snapshot.latitude(),
                snapshot.longitude(),
                snapshot.contentTypeId(),
                snapshot.lawdCode(),
                regionName,
                centerRank,
                snapshot.baseAt(),
                onlineMention,
                tmapRank == null ? TmapRankView.notAvailable() : tmapRank,
                VisitorStatsView.notImported(),
                visitTiming
        );
    }
}
