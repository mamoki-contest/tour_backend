package com.mamoki.tour.domain.placeimage.dto;

import com.mamoki.tour.domain.placeimage.entity.PlaceImage;
import com.mamoki.tour.global.enums.ImageSource;

/**
 * 조회가 쓰는 대표 사진 한 장.
 *
 * <p>목록과 상세가 서로 다른 주소를 쓴다. 카드에는 썸네일을 걸고 상세에는 원본을 건다 —
 * 카드 수십 장에 원본을 걸면 목록이 느려지고, 상세에 썸네일을 걸면 확대했을 때 뭉갠다.
 *
 * @param sourceUrl 이 사진을 찾은 자리. 저작권자의 페이지가 아니다({@link PlaceImage} 참고).
 */
public record PlaceImageView(String thumbnailUrl, String imageUrl, String sourceUrl,
                             ImageSource provider) {

    public static PlaceImageView from(PlaceImage placeImage) {
        return new PlaceImageView(placeImage.getThumbnailUrl(), placeImage.getImageUrl(),
                placeImage.getSourceUrl(), placeImage.getProvider());
    }

    /** 목록 카드가 쓸 주소. 썸네일이 없으면 원본이라도 건다. */
    public String cardImageUrl() {
        return thumbnailUrl != null ? thumbnailUrl : imageUrl;
    }

    /** 상세가 쓸 주소. 원본이 없으면 썸네일이라도 건다. */
    public String detailImageUrl() {
        return imageUrl != null ? imageUrl : thumbnailUrl;
    }
}
