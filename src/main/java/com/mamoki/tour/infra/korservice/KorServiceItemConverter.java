package com.mamoki.tour.infra.korservice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.infra.korservice.dto.KorServiceItem;

/**
 * KorService2 원본 항목을 표준 계약으로 변환한다.
 *
 * <p>공급자는 결측을 빈 문자열로 내려준다. 그대로 두면 `정보 없음`과 `빈 값`을 구분할 수
 * 없으므로 모두 null 로 정규화한다. 좌표나 기준 시점이 깨져 있어도 임의 값을 만들지 않는다.
 */
public final class KorServiceItemConverter {

    public static final String SOURCE = "KorService2";

    private static final DateTimeFormatter MODIFIED_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private KorServiceItemConverter() {
    }

    public static List<AttractionSnapshot> convertAll(List<KorServiceItem> items) {
        return items.stream()
                .map(KorServiceItemConverter::convert)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * @return 표준 식별자나 이름이 없어 카탈로그에 넣을 수 없는 항목이면 null
     */
    public static AttractionSnapshot convert(KorServiceItem item) {
        String contentId = blankToNull(item.contentid());
        String name = blankToNull(item.title());

        if (contentId == null || name == null) {
            return null;
        }

        return new AttractionSnapshot(
                contentId,
                name,
                blankToNull(item.firstimage()),
                joinAddress(item.addr1(), item.addr2()),
                toCoordinate(item.mapy()),
                toCoordinate(item.mapx()),
                blankToNull(item.contenttypeid()),
                toLawdCode(item.lDongRegnCd(), item.lDongSignguCd()),
                toBaseAt(item.modifiedtime()),
                SOURCE
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String joinAddress(String addr1, String addr2) {
        String primary = blankToNull(addr1);
        String secondary = blankToNull(addr2);

        if (primary == null) {
            return secondary;
        }

        return secondary == null ? primary : primary + " " + secondary;
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

    /** 법정동 시·도 코드와 시·군 코드를 5자리로 결합한다. 둘 중 하나라도 없으면 null. */
    private static String toLawdCode(String regionCode, String sigunguCode) {
        String region = blankToNull(regionCode);
        String sigungu = blankToNull(sigunguCode);

        if (region == null || sigungu == null) {
            return null;
        }

        return region + sigungu;
    }

    private static LocalDateTime toBaseAt(String modifiedTime) {
        String value = blankToNull(modifiedTime);

        if (value == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(value, MODIFIED_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
