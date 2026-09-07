package com.mamoki.tour.infra.tatscnctrrate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.visittiming.dto.AttractionForecast;
import com.mamoki.tour.domain.visittiming.dto.DailyConcentration;
import com.mamoki.tour.infra.tatscnctrrate.dto.TatsCnctrRateItem;

/**
 * TatsCnctrRate 원본 항목을 장소별 30일 예측으로 묶는다.
 *
 * <p>공급자는 "관광지 1곳 × 날짜 1일" 을 한 행으로 내려주므로, 장소 단위로 다시 모아야
 * 그 장소의 분포를 볼 수 있다. 묶는 키는 표기 차이를 흡수한 정규화 이름이다.
 *
 * <p>결측은 빈 문자열이 아니라 null 로 정규화한다. 값이 깨진 날은 버리지 않고 rate 를 null 로
 * 남겨, 예측이 없는 날과 예측이 0 인 날을 구분할 수 있게 한다.
 */
public final class TatsCnctrRateItemConverter {

    public static final String SOURCE = "TatsCnctrRateService";

    private static final DateTimeFormatter BASE_YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private TatsCnctrRateItemConverter() {
    }

    public static List<AttractionForecast> convertAll(List<TatsCnctrRateItem> items) {
        Map<String, Builder> byNormalizedName = new LinkedHashMap<>();

        for (TatsCnctrRateItem item : items) {
            String name = blankToNull(item.tAtsNm());
            LocalDate date = toDate(item.baseYmd());

            // 이름이나 날짜가 없으면 어느 장소의 어느 날인지 알 수 없어 쓸 수 없다.
            if (name == null || date == null) {
                continue;
            }

            String normalized = PlaceNameNormalizer.normalize(name);

            if (normalized == null) {
                continue;
            }

            byNormalizedName
                    .computeIfAbsent(normalized, key -> new Builder(name, key))
                    .add(item, date);
        }

        return byNormalizedName.values().stream().map(Builder::build).toList();
    }

    /** 같은 장소의 행을 모으는 중간 상태. 좌표와 시·군 코드는 처음 채워진 값을 쓴다. */
    private static final class Builder {

        private final String name;
        private final String normalizedName;
        private final Map<LocalDate, DailyConcentration> days = new LinkedHashMap<>();

        private String lawdCode;
        private BigDecimal latitude;
        private BigDecimal longitude;

        private Builder(String name, String normalizedName) {
            this.name = name;
            this.normalizedName = normalizedName;
        }

        private void add(TatsCnctrRateItem item, LocalDate date) {
            // 같은 날짜가 두 번 오면 먼저 온 값을 남긴다. 뒤 값으로 덮으면 순서에 따라 결과가 달라진다.
            days.putIfAbsent(date, new DailyConcentration(date, toRate(item.cnctrRate())));

            if (lawdCode == null) {
                lawdCode = blankToNull(item.signguCd());
            }

            if (latitude == null) {
                latitude = toCoordinate(item.mapY());
            }

            if (longitude == null) {
                longitude = toCoordinate(item.mapX());
            }
        }

        private AttractionForecast build() {
            List<DailyConcentration> sorted = new ArrayList<>(days.values());
            sorted.sort(java.util.Comparator.comparing(DailyConcentration::date));

            return new AttractionForecast(
                    name, normalizedName, lawdCode, latitude, longitude, List.copyOf(sorted));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDate toDate(String value) {
        String date = blankToNull(value);

        if (date == null) {
            return null;
        }

        try {
            return LocalDate.parse(date, BASE_YMD);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 값이 없거나 숫자가 아니면 0 으로 채우지 않고 null 로 남긴다. */
    private static BigDecimal toRate(String value) {
        String rate = blankToNull(value);

        if (rate == null) {
            return null;
        }

        try {
            return new BigDecimal(rate);
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
