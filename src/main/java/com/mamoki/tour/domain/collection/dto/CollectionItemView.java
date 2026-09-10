package com.mamoki.tour.domain.collection.dto;

import java.time.LocalDateTime;

import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.collection.enums.CollectionItemStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 저장된 식별자 하나의 재조회 결과.
 *
 * <p>요청한 식별자는 결과에서 빠지지 않는다. 빠뜨리면 프론트가 어느 항목이 처리되지 않았는지
 * 대조해야 하고, 그 과정에서 확인하지 못한 항목이 없어진 항목으로 뭉뚱그려진다.
 */
@Schema(description = "저장된 식별자 하나의 재조회 결과")
public record CollectionItemView(

        @Schema(description = "요청한 표준 관광지 식별자", example = "126508")
        String contentId,

        @Schema(description = """
                AVAILABLE=최신 표시정보를 얻음,
                NOT_FOUND=공급자에 더 이상 없음,
                UNAVAILABLE=공급자를 부르지 못해 이번에는 확인하지 못함""")
        CollectionItemStatus status,

        @Schema(description = "최신 표시정보. AVAILABLE 이 아니면 null")
        AttractionResponse attraction,

        @Schema(description = "이 항목에 사용한 데이터의 수집 시각. 확인하지 못했으면 null")
        LocalDateTime collectedAt
) {

    public static CollectionItemView notFound(String contentId) {
        return new CollectionItemView(contentId, CollectionItemStatus.NOT_FOUND, null, null);
    }

    public static CollectionItemView unavailable(String contentId) {
        return new CollectionItemView(contentId, CollectionItemStatus.UNAVAILABLE, null, null);
    }
}
