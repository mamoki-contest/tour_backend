package com.mamoki.tour.domain.placemapping.support;

import java.util.regex.Pattern;

import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;

/**
 * 매핑 단계에서만 쓰는 한 겹 더 깊은 표기 정규화.
 *
 * <p>카탈로그는 별칭을 괄호로 덧붙이고({@code 사근진해변(사근진해수욕장)}) 시·군명을
 * 앞에 붙인다({@code 삼척 죽서루}). 원천 파일은 그러지 않는다. 그 두 겹만 걷어내면
 * 같은 장소가 같은 이름이 된다.
 *
 * <p><b>{@link PlaceNameNormalizer} 를 건드리지 않는 이유.</b> 그 규칙은 언급량 수집의
 * 검색어와 TMAP·입장객 적재의 매칭이 모두 쓴다. 거기에 접두어 절단을 넣으면
 * {@code queryRuleVersion} 이 바뀌어 지난 달 스냅샷과 견줄 수 없게 되고, 이미 이어져 있던
 * 행이 말없이 다른 곳에 붙을 수 있다. 여기서 걷어낸 이름은 매핑 판정에만 쓰고 어디에도
 * 저장하지 않는다.
 *
 * <p>걷어내는 것은 <b>되찾기 위해서지 좁히기 위해서가 아니다.</b> 걷어낸 결과로 카탈로그
 * 두 곳이 같아지면 그때는 확정하지 않는다({@link NameVariantIndex}).
 */
public final class MappingNameNormalizer {

    /** 괄호·대괄호와 전각 짝. 안의 별칭·운영 상태·지정번호를 통째로 걷어낸다. */
    private static final Pattern BRACKETED =
            Pattern.compile("[\\(\\[（［][^\\(\\[（［\\)\\]）］]*[\\)\\]）］]");

    /**
     * 시·도 접두어. 2023년 개칭으로 두 표기가 섞여 있다. 긴 것부터 본다.
     *
     * <p>{@code 강원} 만 떼지는 않는다. {@code 강원랜드}·{@code 강원경찰박물관} 처럼
     * 접두어가 아니라 이름의 일부인 경우가 있어, 떼는 순간 다른 장소가 된다.
     */
    private static final String[] PROVINCE_PREFIXES = {"강원특별자치도", "강원도"};

    /**
     * 걷어낸 뒤 남아야 하는 최소 길이.
     *
     * <p>한 글자만 남으면 걷어내다 다른 장소와 겹칠 수 있다. {@code 강원도청} 이
     * {@code 청} 이 되는 식이다. 그런 이름은 아예 되찾지 않는다.
     */
    private static final int MIN_LENGTH = 2;

    private MappingNameNormalizer() {
    }

    /**
     * @param name       걷어낼 이름. 카탈로그 쪽과 원천 쪽에 같은 규칙을 쓴다.
     * @param regionName 대상 시·군명({@code 강릉시}). 모르면 시·군 접두어는 건드리지 않는다.
     * @return 정규화한 이름. 걷어내고 나면 너무 짧거나 비면 null 이다.
     */
    public static String normalize(String name, String regionName) {
        if (name == null) {
            return null;
        }

        String stripped = stripRegionPrefix(stripProvincePrefix(stripBrackets(name)), regionName);
        String normalized = PlaceNameNormalizer.normalize(stripped);

        return normalized != null && normalized.length() >= MIN_LENGTH ? normalized : null;
    }

    private static String stripBrackets(String name) {
        String previous = null;
        String current = name;

        // 괄호가 겹쳐 있을 수 있다: `장릉(단종) [유네스코 세계유산]`.
        while (!current.equals(previous)) {
            previous = current;
            current = BRACKETED.matcher(current).replaceAll(" ");
        }

        return current.strip();
    }

    private static String stripProvincePrefix(String name) {
        for (String prefix : PROVINCE_PREFIXES) {
            if (name.startsWith(prefix) && name.length() > prefix.length()) {
                return name.substring(prefix.length()).strip();
            }
        }

        return name;
    }

    /**
     * 시·군명을 앞에서 걷어낸다.
     *
     * <p>{@code 강릉시 교동} 과 {@code 강릉 교동} 이 둘 다 쓰여 시·군명 그대로와 접미사를
     * 뗀 형태를 함께 본다. 긴 쪽을 먼저 보아야 {@code 강릉시} 에서 {@code 시} 가 남지 않는다.
     */
    private static String stripRegionPrefix(String name, String regionName) {
        if (regionName == null || regionName.isBlank()) {
            return name;
        }

        String region = regionName.strip();
        String shortened = endsWithDistrictSuffix(region) ? region.substring(0, region.length() - 1) : null;

        for (String prefix : new String[] {region, shortened}) {
            if (prefix != null && !prefix.isEmpty()
                    && name.startsWith(prefix) && name.length() > prefix.length()) {
                return name.substring(prefix.length()).strip();
            }
        }

        return name;
    }

    private static boolean endsWithDistrictSuffix(String region) {
        char last = region.charAt(region.length() - 1);

        return last == '시' || last == '군' || last == '구';
    }
}
