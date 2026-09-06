package com.mamoki.tour.domain.attraction.support;

import java.text.Normalizer;

/**
 * 공급자 간 관광지명 매칭을 위한 정규화.
 *
 * <p>같은 장소를 공급자마다 다르게 적는다. 예를 들어 중심관광지는 {@code 아르떼뮤지엄/강릉},
 * 관심도 CSV 는 {@code 아르떼뮤지엄강릉} 으로 표기한다. 공백과 구분 기호를 걷어내면 같아진다.
 *
 * <p>여기서는 표기 차이만 흡수한다. 서로 다른 장소를 같다고 판단할 수 있는 부분 일치나
 * 접두어 절단은 하지 않는다. 그 판단은 좌표까지 확인한 뒤에 내린다.
 */
public final class PlaceNameNormalizer {

    private PlaceNameNormalizer() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return null;
        }

        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFKC).toLowerCase();
        StringBuilder normalized = new StringBuilder(decomposed.length());

        // 글자와 숫자만 남긴다. 공백, 구분 기호, 괄호, 문장부호는 모두 표기 차이로 본다.
        decomposed.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(normalized::appendCodePoint);

        return normalized.isEmpty() ? null : normalized.toString();
    }
}
