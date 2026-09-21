package com.mamoki.tour.domain.attraction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mamoki.tour.domain.attraction.support.ImageUrls;
import com.mamoki.tour.domain.placeimage.dto.PlaceImageView;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;
import com.mamoki.tour.global.enums.ImageSource;

/**
 * 관광지 목록 항목의 표준 계약.
 *
 * <p>결측은 null 로 내려간다. 프론트는 null 을 `정보 없음`으로 표시하고 0 으로 해석하지 않는다.
 *
 * @param regionName 지역코드 매핑에서 찾은 시·군 이름. 매핑이 없으면 null.
 * @param centerRank 시·군 내부 중심관광지 순위. 매칭되지 않으면 null이며, 순위 없음이 낮은 순위를 뜻하지 않는다.
 * @param imageSource 대표 이미지의 출처. 이미지가 없으면 null.
 * @param imageSourceUrl 공급자 사진이 아닐 때 그 사진을 찾은 자리. 공급자 사진이면 null.
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

        @Schema(description = """
                대표 이미지의 출처. 이미지가 없으면 null 입니다.
                KOR_SERVICE 는 한국관광공사가 준 사진이고, NAVER_IMAGE 는 공급자 사진이 없어
                네이버 이미지 검색으로 채운 제3자 사진입니다. NAVER_IMAGE 는 화면에 출처를
                함께 보여야 합니다.""")
        ImageSource imageSource,

        @Schema(description = """
                그 사진을 찾은 자리(네이버 이미지 검색 결과). imageSource 가 NAVER_IMAGE 일 때만
                채워집니다. 저작권자의 페이지가 아닙니다 - 이미지 검색 응답에 원문 글의 주소가
                없어 만들 수 없습니다.""")
        String imageSourceUrl,

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
                status 는 이 장소 자신의 지원 범위 안 분포에서의 상대 수준이며,
                다른 관광지의 status 와 비교해 혼잡도 순위로 쓰면 안 됩니다.""")
        VisitTiming visitTiming
) {

    public static AttractionResponse of(AttractionSnapshot snapshot, String regionName) {
        return of(snapshot, regionName, null, null, null, null, null);
    }

    public static AttractionResponse of(AttractionSnapshot snapshot, String regionName, Integer centerRank) {
        return of(snapshot, regionName, centerRank, null, null, null, null);
    }

    public static AttractionResponse of(AttractionSnapshot snapshot, String regionName,
                                        Integer centerRank, OnlineMentionView onlineMention,
                                        TmapRankView tmapRank) {
        return of(snapshot, regionName, centerRank, onlineMention, tmapRank, null, null);
    }

    /**
     * 신호는 각각 독립 필드로 담는다. 값이 없으면 상태로 알리고 0 이나 낮은 순위로 채우지 않는다.
     *
     * <p>온라인 언급량·TMAP 순위·입장객 수·날짜 탐색은 서로 다른 데이터다. 하나의 점수로 합치지
     * 않으며, 어느 하나의 결측을 다른 신호로 추정하지도 않는다.
     */
    public static AttractionResponse of(AttractionSnapshot snapshot, String regionName,
                                        Integer centerRank, OnlineMentionView onlineMention,
                                        TmapRankView tmapRank, VisitorStatsView visitorStats,
                                        VisitTiming visitTiming) {
        return new AttractionResponse(
                snapshot.contentId(),
                snapshot.name(),
                snapshot.imageUrl(),
                ImageUrls.hasImage(snapshot.imageUrl()) ? ImageSource.KOR_SERVICE : null,
                null,
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
                visitorStats == null ? VisitorStatsView.notImported() : visitorStats,
                visitTiming
        );
    }

    /**
     * 공급자 사진이 없는 자리에만 찾아 둔 사진을 건다(#99).
     *
     * <p>공급자 사진이 있으면 이 항목을 그대로 돌려준다. 우리가 찾은 제3자 사진으로 덮으면
     * 허락받은 사진이 허락받지 않은 사진으로 조용히 바뀌고, 화면만 봐서는 알 수 없다.
     *
     * <p>목록이라 썸네일을 건다. 카드 수십 장에 원본을 걸면 목록이 느려진다.
     */
    public AttractionResponse withFallbackImage(PlaceImageView fallback) {
        if (ImageUrls.hasImage(imageUrl) || fallback == null || fallback.cardImageUrl() == null) {
            return this;
        }

        return new AttractionResponse(contentId, name, fallback.cardImageUrl(),
                fallback.provider(), fallback.sourceUrl(), address, latitude, longitude,
                contentTypeId, lawdCode, regionName, centerRank, baseAt, onlineMention,
                tmapRank, visitorStats, visitTiming);
    }

}
