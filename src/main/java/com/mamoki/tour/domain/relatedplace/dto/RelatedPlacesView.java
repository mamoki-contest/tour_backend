package com.mamoki.tour.domain.relatedplace.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mamoki.tour.domain.relatedplace.enums.RelatedPlacesStatus;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.infra.tarrltetar.TarRlteTarItemConverter;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 연관 장소 묶음 하나(대체지 후보 또는 함께 가기 좋은 곳).
 *
 * <p>{@code items} 가 비어 있는 이유를 {@code status} 로 구분한다. 빈 목록만 내려주면
 * 프론트가 "공급자 데이터 없음" 과 "자격을 충족한 곳 없음" 을 같은 문구로 표시하게 되고,
 * 그것은 없는 사실을 만들어내는 것이다.
 *
 * @param dataStatus  공급자 데이터의 신선도. {@code status} 와 의미가 다르다.
 * @param baseYm      공급자 조회 기준 월(yyyyMM). 데이터랩 계열은 월 단위로 갱신된다.
 * @param collectedAt 이 데이터를 수집한 시각. 정보 없음이면 null.
 */
@Schema(description = "연관 장소 묶음. items 가 비어 있으면 status 로 그 이유를 확인하세요.")
public record RelatedPlacesView(

        @Schema(description = """
                AVAILABLE=항목 있음,
                NO_RELATED_DATA=공급자 응답을 얻지 못했거나 이 관광지가 공급자 연관 목록에 없음,
                NONE_QUALIFIED=연관 장소는 받았으나 이 묶음의 자격을 충족한 곳이 없음""")
        RelatedPlacesStatus status,

        @Schema(description = "연관 장소 목록. 공급자 연관 순위 오름차순")
        List<RelatedPlace> items,

        @Schema(description = "공급자 데이터의 신선도. AVAILABLE / STALE / NO_DATA")
        DataStatus dataStatus,

        @Schema(description = "공급자 조회 기준 월(yyyyMM). 정보 없음이면 null", example = "202607")
        String baseYm,

        @Schema(description = "데이터를 수집한 시각. 정보 없음이면 null")
        LocalDateTime collectedAt,

        @Schema(description = "데이터 출처", example = "TarRlteTarService1")
        String source
) {

    public static RelatedPlacesView of(List<RelatedPlace> items, boolean hasRelatedData,
                                       DataStatus dataStatus, String baseYm,
                                       LocalDateTime collectedAt) {

        return new RelatedPlacesView(
                resolveStatus(items, hasRelatedData),
                items,
                dataStatus,
                baseYm,
                collectedAt,
                TarRlteTarItemConverter.SOURCE);
    }

    /** 연관 데이터를 받지 못한 것과, 받았지만 자격을 충족한 곳이 없는 것을 구분한다. */
    private static RelatedPlacesStatus resolveStatus(List<RelatedPlace> items, boolean hasRelatedData) {
        if (!items.isEmpty()) {
            return RelatedPlacesStatus.AVAILABLE;
        }

        return hasRelatedData ? RelatedPlacesStatus.NONE_QUALIFIED : RelatedPlacesStatus.NO_RELATED_DATA;
    }

    public static RelatedPlacesView noData(String baseYm) {
        return new RelatedPlacesView(RelatedPlacesStatus.NO_RELATED_DATA, List.of(),
                DataStatus.NO_DATA, baseYm, null, TarRlteTarItemConverter.SOURCE);
    }
}
