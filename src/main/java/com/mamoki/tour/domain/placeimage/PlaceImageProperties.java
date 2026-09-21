package com.mamoki.tour.domain.placeimage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대표 사진 보강 배치 설정(#99).
 *
 * @param maxCallsPerRun 한 번의 실행에서 낼 수 있는 이미지 검색 호출 수의 상한.
 *                       공급자 하루 한도가 25,000회이고 그 한도는 <b>블로그 검색과 같은
 *                       키를 공유</b>한다. 이 배치가 한 번에 다 쓰면 그달 언급량 수집이
 *                       멈춘다.
 * @param display        한 번에 받을 항목 수. 첫 항목이 주소 없는 껍데기일 수 있어 몇 장을
 *                       함께 받아 첫 유효 항목을 쓴다. 받는 수가 늘어도 호출 수는 그대로다.
 * @param filter         이미지 크기 필터. {@code medium} 은 카드·상세에 쓸 만한 크기이면서
 *                       원본이 수 MB 짜리 사진으로 튀지 않는 선이다.
 */
@ConfigurationProperties(prefix = "tour.place-image")
public record PlaceImageProperties(int maxCallsPerRun, int display, String filter) {

    /**
     * 기본 20,000회.
     *
     * <p>하루 한도(25,000)를 다 쓰지 않는다. 강원 카탈로그가 4,700여 곳이라 사진 없는
     * 관광지를 한 번에 다 도는 데 부족하지 않고, 남는 5,000회는 같은 키를 쓰는 언급량
     * 수집의 몫으로 남겨 둔다.
     */
    private static final int DEFAULT_MAX_CALLS_PER_RUN = 20_000;

    private static final int DEFAULT_DISPLAY = 3;
    private static final String DEFAULT_FILTER = "medium";

    public PlaceImageProperties {
        maxCallsPerRun = maxCallsPerRun > 0 ? maxCallsPerRun : DEFAULT_MAX_CALLS_PER_RUN;
        display = display > 0 ? display : DEFAULT_DISPLAY;
        filter = filter == null || filter.isBlank() ? DEFAULT_FILTER : filter.strip();
    }
}
