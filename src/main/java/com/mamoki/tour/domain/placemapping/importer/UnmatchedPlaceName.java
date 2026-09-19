package com.mamoki.tour.domain.placemapping.importer;

import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;

/**
 * 카탈로그에 잇지 못한 원천 이름 하나.
 *
 * <p>시·군은 판정에 두 가지로 쓰인다. 카카오 검색을 그 주변으로 좁히는 데, 그리고 이을
 * 카탈로그 후보를 그 시·군으로 한정하는 데 쓴다. 시·군을 모르면 강원 전역이 후보가 되어
 * 같은 이름의 다른 장소가 붙을 수 있으므로, 그런 이름은 판정하지 않는다.
 *
 * @param lawdCode   법정동 시·군 코드. 원천의 지역 표기가 시드에 없으면 null.
 * @param regionName 시드가 적은 시·군명. 카카오 결과를 이 시·군으로 좁히는 데 쓴다.
 */
public record UnmatchedPlaceName(String sourceName, String normalizedName,
                                 String lawdCode, String regionName) {

    public static UnmatchedPlaceName of(String sourceName, String lawdCode, String regionName) {
        return new UnmatchedPlaceName(sourceName, PlaceNameNormalizer.normalize(sourceName),
                lawdCode, regionName);
    }

    /** 판정할 수 있는 이름인지. 이름을 정규화할 수 없거나 시·군을 모르면 판정하지 않는다. */
    public boolean isDecidable() {
        return normalizedName != null && lawdCode != null && regionName != null;
    }

    /** 같은 이름을 두 번 판정하지 않도록 묶는 키. */
    public String key() {
        return lawdCode + "|" + normalizedName;
    }
}
