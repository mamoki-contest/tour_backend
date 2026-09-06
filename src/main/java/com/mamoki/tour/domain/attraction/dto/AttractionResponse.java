package com.mamoki.tour.domain.attraction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관광지 목록 항목의 표준 계약.
 *
 * <p>결측은 null 로 내려간다. 프론트는 null 을 `정보 없음`으로 표시하고 0 으로 해석하지 않는다.
 *
 * @param regionName 지역코드 매핑에서 찾은 시·군 이름. 매핑이 없으면 null.
 * @param centerRank 시·군 내부 중심관광지 순위. 매칭되지 않으면 null이며, 순위 없음이 낮은 순위를 뜻하지 않는다.
 * @param baseAt     이 항목의 공급자 기준 시점.
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
        LocalDateTime baseAt
) {

    public static AttractionResponse of(AttractionSnapshot snapshot, String regionName) {
        return of(snapshot, regionName, null);
    }

    public static AttractionResponse of(AttractionSnapshot snapshot, String regionName, Integer centerRank) {
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
                snapshot.baseAt()
        );
    }
}
