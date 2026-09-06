package com.mamoki.tour.domain.attraction.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mamoki.tour.global.enums.DataStatus;

/**
 * 관광지 목록 응답.
 *
 * <p>공급자 장애나 결측을 빈 목록으로 위장하지 않는다. dataStatus 로 상태를 구분하고,
 * collectedAt 으로 이 데이터를 언제 수집했는지 함께 알린다.
 *
 * @param dataStatus  AVAILABLE(유효) / STALE(최종 정상 데이터) / NO_DATA(정보 없음)
 * @param collectedAt 응답에 사용한 데이터의 수집 시각. NO_DATA 이면 null.
 */
public record AttractionListResponse(
        List<AttractionResponse> items,
        int totalCount,
        int page,
        int size,
        DataStatus dataStatus,
        LocalDateTime collectedAt,
        String source
) {

    public static AttractionListResponse noData(int page, int size, String source) {
        return new AttractionListResponse(List.of(), 0, page, size, DataStatus.NO_DATA, null, source);
    }
}
