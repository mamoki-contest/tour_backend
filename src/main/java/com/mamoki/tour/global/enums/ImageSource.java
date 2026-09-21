package com.mamoki.tour.global.enums;

/**
 * 대표 사진이 어디서 온 것인지.
 *
 * <p>사진마다 출처가 다르고 그에 따라 <b>쓸 수 있는 조건도 다르다</b>. 공급자 사진은
 * 한국관광공사가 제공한 것이고, 네이버 이미지 검색으로 채운 사진은 제3자 저작물이라
 * 화면에 출처를 함께 보여야 한다. 프론트가 둘을 가르려면 응답에 이 값이 있어야 한다.
 *
 * <p>{@code place_image} 표의 {@code provider} 컬럼과 조회 응답의 {@code imageSource}
 * 필드가 같은 이름을 쓴다. 표에는 오늘 {@link #NAVER_IMAGE} 만 쌓이지만, 열거형을 둘로
 * 나누면 "저장할 때의 이름" 과 "내려보낼 때의 이름" 이 따로 늙는다.
 */
public enum ImageSource {

    /** 한국관광공사 KorService2 의 {@code firstimage}. 카탈로그에 함께 들어 있다. */
    KOR_SERVICE,

    /**
     * 네이버 이미지 검색으로 찾은 제3자 사진(#99).
     *
     * <p>공모전 제출용(비상업)이라는 전제에서만 쓴다. 저작권자에게 이용 허락을 받은
     * 사진이 아니다.
     */
    NAVER_IMAGE
}
