package com.mamoki.tour.infra.tarrltetar;

import java.util.List;
import java.util.Objects;

import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.relatedplace.dto.RelatedPlaceRow;
import com.mamoki.tour.domain.relatedplace.support.RelatedPlaceClassifier;
import com.mamoki.tour.infra.tarrltetar.dto.TarRlteTarItem;

/**
 * TarRlteTarService1 원본 행을 표준 계약으로 변환한다.
 *
 * <p>공급자는 결측을 빈 문자열로 내려준다. 그대로 두면 `정보 없음`과 `빈 값`을 구분할 수
 * 없으므로 모두 null 로 정규화한다. 순위가 숫자가 아니면 0 으로 채우지 않고 null 로 남긴다.
 */
public final class TarRlteTarItemConverter {

    public static final String SOURCE = "TarRlteTarService1";

    private TarRlteTarItemConverter() {
    }

    public static List<RelatedPlaceRow> convertAll(List<TarRlteTarItem> items) {
        return items.stream()
                .map(TarRlteTarItemConverter::convert)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * @return 기준 관광지명이나 연관 장소명이 없어 어느 장소의 무엇인지 알 수 없으면 null
     */
    public static RelatedPlaceRow convert(TarRlteTarItem item) {
        String baseName = blankToNull(item.tAtsNm());
        String name = blankToNull(item.rlteTatsNm());

        if (baseName == null || name == null) {
            return null;
        }

        String categoryLarge = blankToNull(item.rlteCtgryLclsNm());

        return new RelatedPlaceRow(
                baseName,
                PlaceNameNormalizer.normalize(baseName),
                name,
                PlaceNameNormalizer.normalize(name),
                RelatedPlaceClassifier.classify(categoryLarge),
                categoryLarge,
                blankToNull(item.rlteCtgryMclsNm()),
                blankToNull(item.rlteCtgrySclsNm()),
                toLawdCode(item.rlteSignguCd()),
                blankToNull(item.rlteSignguNm()),
                toRank(item.rlteRank())
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 공급자는 연관 장소의 시·군을 법정동 5자리로 준다. 형식이 어긋나면 호출 키를 만들 수 없어 버린다. */
    private static String toLawdCode(String value) {
        String lawdCode = blankToNull(value);

        if (lawdCode == null || lawdCode.length() != 5
                || !lawdCode.chars().allMatch(Character::isDigit)) {
            return null;
        }

        return lawdCode;
    }

    private static Integer toRank(String value) {
        String rank = blankToNull(value);

        if (rank == null) {
            return null;
        }

        try {
            return Integer.valueOf(rank);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
