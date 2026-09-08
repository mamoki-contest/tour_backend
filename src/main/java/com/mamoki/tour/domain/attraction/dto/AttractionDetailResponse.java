package com.mamoki.tour.domain.attraction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.mamoki.tour.domain.relatedplace.dto.RelatedPlacesView;
import com.mamoki.tour.domain.visittiming.dto.DailyVisitTiming;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;
import com.mamoki.tour.global.enums.DataStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관광지 상세의 표준 계약.
 *
 * <p>기본정보, 30일 방문 혼잡도 예측, 대체지 후보, 함께 가기 좋은 곳을 각각 독립 필드로 둔다.
 * 하나의 점수로 합치지 않고, 어느 하나의 결측을 다른 신호로 추정하지도 않는다.
 *
 * @param dataStatus  기본정보의 신선도. 연관 장소와 예측은 각자의 상태를 따로 갖는다.
 * @param collectedAt 기본정보를 수집한 시각. 서버 저장 시각이 아니라 공급자 응답 기준이다.
 */
@Schema(description = """
        관광지 상세. 기본정보·30일 예측·대체지 후보·함께 가기 좋은 곳이 각각 독립 필드입니다.
        결측은 모두 null 또는 명시적 상태이며 0 으로 해석하면 안 됩니다.""")
public record AttractionDetailResponse(

        @Schema(description = "표준 관광지 식별자", example = "126508")
        String contentId,

        @Schema(description = "관광지명", example = "국립산악박물관")
        String name,

        @Schema(description = "대표 이미지. 없으면 null")
        String imageUrl,

        @Schema(description = "주소. 없으면 null")
        String address,

        @Schema(description = "우편번호. 없으면 null", example = "03045")
        String zipcode,

        @Schema(description = "전화번호. 없으면 null")
        String tel,

        @Schema(description = "홈페이지. 공급자가 앵커 태그를 그대로 주기도 합니다. 없으면 null")
        String homepage,

        @Schema(description = "개요 설명. 없으면 null")
        String overview,

        @Schema(description = "위도. 좌표가 없으면 null")
        BigDecimal latitude,

        @Schema(description = "경도. 좌표가 없으면 null")
        BigDecimal longitude,

        @Schema(description = "관광지 분류", example = "12")
        String contentTypeId,

        @Schema(description = "법정동 시·군 코드 5자리", example = "51150")
        String lawdCode,

        @Schema(description = "시·군 이름. 지역코드 매핑이 없으면 null", example = "강릉시")
        String regionName,

        @Schema(description = "기본정보의 공급자 기준 시점")
        LocalDateTime baseAt,

        @Schema(description = "기본정보의 신선도. AVAILABLE / STALE")
        DataStatus dataStatus,

        @Schema(description = "기본정보를 수집한 시각")
        LocalDateTime collectedAt,

        @Schema(description = "기본정보 출처", example = "KorService2")
        String source,

        @Schema(description = """
                이 장소의 유연 모드 판정. 향후 30일 중 한산 예상일과 지원 범위를 담습니다.
                status 는 이 장소 자신의 분포 안에서의 상대 수준이며, 다른 관광지와 비교하면 안 됩니다.""")
        VisitTiming visitTiming,

        @Schema(description = """
                지원 범위 30일의 하루치 판정. 날짜 오름차순입니다.
                집중률 원본값은 담지 않습니다. 값을 노출하면 장소 사이의 절대 순위를 만들 수 있습니다.""")
        List<DailyVisitTiming> dailyForecast,

        @Schema(description = """
                대체지 후보. 원래 장소가 아닌 관광지 중 유효한 방문 혼잡도 예측을 가진 곳만 담깁니다.
                비어 있으면 status 로 그 이유를 확인하세요.""")
        RelatedPlacesView alternatives,

        @Schema(description = "함께 가기 좋은 곳. 음식점·숙박시설이며 대체지가 아닙니다.")
        RelatedPlacesView companions
) {
}
