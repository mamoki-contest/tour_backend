package com.mamoki.tour.infra.locgohub;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.mamoki.tour.domain.attraction.dto.CenterRank;
import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.infra.locgohub.dto.LocgoHubItem;

/**
 * LocgoHub 원본 항목을 중심관광지 순위 계약으로 변환한다.
 *
 * <p>KorService2 와 마찬가지로 결측은 빈 문자열이 아니라 null 로 정규화한다.
 * 순위나 이름이 없어 쓸 수 없는 항목은 만들어내지 않고 버린다.
 */
public final class LocgoHubItemConverter {

    public static final String SOURCE = "LocgoHubTarService1";

    private LocgoHubItemConverter() {
    }

    public static List<CenterRank> convertAll(List<LocgoHubItem> items) {
        return items.stream()
                .map(LocgoHubItemConverter::convert)
                .filter(Objects::nonNull)
                .toList();
    }

    public static CenterRank convert(LocgoHubItem item) {
        String name = blankToNull(item.hubTatsNm());
        Integer rank = toRank(item.hubRank());

        if (name == null || rank == null) {
            return null;
        }

        return new CenterRank(
                blankToNull(item.hubTatsCd()),
                name,
                PlaceNameNormalizer.normalize(name),
                toCoordinate(item.mapY()),
                toCoordinate(item.mapX()),
                blankToNull(item.signguCd()),
                rank,
                blankToNull(item.baseYm())
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private static BigDecimal toCoordinate(String value) {
        String coordinate = blankToNull(value);

        if (coordinate == null) {
            return null;
        }

        try {
            return new BigDecimal(coordinate);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
