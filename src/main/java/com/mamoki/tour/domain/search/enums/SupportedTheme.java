package com.mamoki.tour.domain.search.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * 검증된 추천을 보장하는 지원 테마의 <b>코드</b>.
 *
 * <p>여기 없는 코드는 지원 테마가 아니다. 비슷해 보인다는 이유로 늘리지 않는다.
 * 지원 테마라는 표시는 추천 자격을 적용했다는 뜻이고, 자격을 확인할 수 없는 말에
 * 그 표시를 붙이면 보증이 의미를 잃는다.
 *
 * <p>이 enum 은 <b>코드만</b> 가진다. 표시명·동의어·공급자 검색어·자격 토큰은
 * {@code supported_theme} · {@code supported_theme_synonym} 테이블에 있고
 * {@code data.sql} 시드로 채운다(#54). 응답 계약의 {@code code} 값이 이 이름이라
 * enum 자체는 남겨 두었다. 코드를 늘리려면 여기와 시드를 함께 고쳐야 하며,
 * 시드에만 있는 코드는 무시된다({@link #from(String)}).
 */
public enum SupportedTheme {

    CHERRY_BLOSSOM,
    FLOWER_FESTIVAL,
    BEACH,
    VALLEY,
    AUTUMN_FOLIAGE,
    SILVER_GRASS,
    SNOW_FLOWER,
    SUNRISE;

    /**
     * 시드에 적힌 코드 문자열을 테마 코드로 옮긴다.
     *
     * <p>모르는 코드는 빈 값이다. 시드가 앞서가거나 오타가 났을 때 임의의 테마로
     * 넘겨짚지 않는다. 호출하는 쪽이 이 사실을 기록에 남긴다.
     */
    public static Optional<SupportedTheme> from(String code) {
        if (code == null) {
            return Optional.empty();
        }

        return Arrays.stream(values())
                .filter(theme -> theme.name().equals(code))
                .findFirst();
    }
}
