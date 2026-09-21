package com.mamoki.tour.domain.attraction.support;

/**
 * 공급자 사진이 "있다" 를 판정하는 한 가지 규칙.
 *
 * <p>공급자(KorService2)는 사진이 없을 때 {@code firstimage} 를 <b>빈 문자열</b>로 준다.
 * null 만 없는 것으로 보면 그 자리가 영영 채워지지 않는다. 목록·상세·배치가 같은 답을
 * 내야 하므로 규칙을 여기 한 곳에 둔다.
 */
public final class ImageUrls {

    private ImageUrls() {
    }

    public static boolean hasImage(String imageUrl) {
        return imageUrl != null && !imageUrl.isBlank();
    }
}
