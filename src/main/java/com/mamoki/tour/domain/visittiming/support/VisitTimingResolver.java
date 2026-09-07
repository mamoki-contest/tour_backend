package com.mamoki.tour.domain.visittiming.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.mamoki.tour.domain.visittiming.dto.AttractionForecast;
import com.mamoki.tour.domain.visittiming.dto.DailyConcentration;
import com.mamoki.tour.domain.visittiming.dto.VisitTimingVerdict;
import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;

/**
 * 집중률 예측을 장소 내부 상대 수준으로 해석하는 규칙.
 *
 * <p>공급자는 집중률의 공식 등급 기준을 제공하지 않는다. 그래서 값 자체를 한산·보통·혼잡으로
 * 끊을 근거가 없다. 대신 <b>그 장소의 향후 30일 분포</b> 안에서 어디쯤인지만 본다.
 * 이렇게 하면 어떤 장소의 HIGH 와 다른 장소의 HIGH 를 비교할 수 없게 되는데, PRD 가
 * 금지한 절대 혼잡도 순위를 애초에 만들 수 없게 하려는 것이 목적이다.
 *
 * <p>규칙은 다음 세 개다. 임의로 바꾸면 프론트 표시 문구와 어긋나므로 상수로 고정한다.
 * <ol>
 *   <li>정렬한 유효 예측의 하위 1/3 은 {@code LOW}, 상위 1/3 은 {@code HIGH}, 나머지는 {@code NORMAL}.
 *       경계값이 같으면 같은 등급이 되도록 순번이 아니라 값으로 끊는다.</li>
 *   <li>유효 예측일이 {@link #MIN_FORECAST_DAYS_FOR_LEVEL} 일 미만이면 판정하지 않고 {@code NO_DATA}.
 *       30일 창의 1/3 도 모이지 않은 분포로 상대 수준을 말할 수 없다.</li>
 *   <li>같은 값이 분포의 대부분을 덮어 두 경계가 붙으면, 그 값은 이 장소의 보통({@code NORMAL})이고
 *       그보다 낮은 날만 {@code LOW}, 높은 날만 {@code HIGH} 다.
 *       유연 모드는 30일 예측이 모두 같을 때만 {@code NO_DATA} 다.
 *       더 한산한 날이 없는데 하나를 골라 주면 없는 한산함을 만들어내는 것이다.</li>
 * </ol>
 */
public final class VisitTimingResolver {

    /** 공급자가 제공하는 예측 범위. 조회일 포함 30일. */
    public static final int FORECAST_WINDOW_DAYS = 30;

    /** 이보다 적은 유효 예측일로는 분포를 신뢰할 수 없어 판정하지 않는다. 30일의 1/3. */
    public static final int MIN_FORECAST_DAYS_FOR_LEVEL = 10;

    private VisitTimingResolver() {
    }

    public static LocalDate supportedFrom(LocalDate today) {
        return today;
    }

    public static LocalDate supportedTo(LocalDate today) {
        return today.plusDays(FORECAST_WINDOW_DAYS - 1L);
    }

    public static boolean isSupported(LocalDate date, LocalDate today) {
        return date != null
                && !date.isBefore(supportedFrom(today))
                && !date.isAfter(supportedTo(today));
    }

    /**
     * 날짜 확정 모드. 선택일을 그 장소 자신의 30일 분포 안에서 해석한다.
     *
     * @param forecast 이 장소의 예측. 매칭되지 않았으면 null 이며 정보 없음으로 처리한다.
     */
    public static VisitTimingVerdict resolveFixed(AttractionForecast forecast,
                                                  LocalDate selectedDate, LocalDate today) {

        // 범위 밖은 데이터 문제가 아니라 요청 문제다. 예측을 보기 전에 먼저 가른다.
        if (!isSupported(selectedDate, today)) {
            return VisitTimingVerdict.outOfRange(selectedDate, 0);
        }

        List<DailyConcentration> valid = validDays(forecast, today);

        if (valid.size() < MIN_FORECAST_DAYS_FOR_LEVEL) {
            return VisitTimingVerdict.noData(selectedDate, valid.size());
        }

        BigDecimal selectedRate = rateOf(valid, selectedDate);

        // 다른 날 예측이 있어도 그 날이 비어 있으면 이웃 값으로 메우지 않는다.
        if (selectedRate == null) {
            return VisitTimingVerdict.noData(selectedDate, valid.size());
        }

        return new VisitTimingVerdict(
                classify(selectedRate, sortedRates(valid)), selectedDate, null, valid.size());
    }

    /** 날짜 유연 모드. 그 장소의 30일 중 상대적으로 한산한 예상일을 고른다. */
    public static VisitTimingVerdict resolveFlexible(AttractionForecast forecast, LocalDate today) {
        List<DailyConcentration> valid = validDays(forecast, today);

        if (valid.size() < MIN_FORECAST_DAYS_FOR_LEVEL) {
            return VisitTimingVerdict.noData(null, valid.size());
        }

        List<BigDecimal> sorted = sortedRates(valid);

        // 모든 날의 예측이 같으면 더 한산한 날이 없다. 아무 날이나 골라 한산하다고 하지 않는다.
        if (sorted.get(0).compareTo(sorted.get(sorted.size() - 1)) == 0) {
            return VisitTimingVerdict.noData(null, valid.size());
        }

        // 최솟값이 여럿이면 가장 이른 날. 같은 조건이면 빨리 갈 수 있는 날이 낫다.
        DailyConcentration quietest = valid.stream()
                .min(Comparator.comparing(DailyConcentration::rate)
                        .thenComparing(DailyConcentration::date))
                .orElseThrow();

        return new VisitTimingVerdict(
                VisitTimingStatus.LOW, null, quietest.date(), valid.size());
    }

    /**
     * 값으로 삼분위를 끊는다. 순번으로 끊으면 값이 같은 날이 서로 다른 등급을 받는다.
     *
     * <p>{@code sorted} 는 오름차순 유효 예측값이며 비어 있지 않다.
     */
    private static VisitTimingStatus classify(BigDecimal rate, List<BigDecimal> sorted) {
        BigDecimal lower = lowerBound(sorted);
        BigDecimal upper = upperBound(sorted);

        // 같은 값이 30일의 3분의 2를 덮으면 두 경계가 붙는다. 이때 그 값은 이 장소의 보통이다.
        // 모든 날의 예측이 같은 경우도 여기로 들어와 NORMAL 이 된다.
        if (lower.compareTo(upper) == 0) {
            int comparison = rate.compareTo(lower);

            if (comparison < 0) {
                return VisitTimingStatus.LOW;
            }

            return comparison > 0 ? VisitTimingStatus.HIGH : VisitTimingStatus.NORMAL;
        }

        if (rate.compareTo(lower) <= 0) {
            return VisitTimingStatus.LOW;
        }

        if (rate.compareTo(upper) >= 0) {
            return VisitTimingStatus.HIGH;
        }

        return VisitTimingStatus.NORMAL;
    }

    /** 하위 1/3 의 마지막 값. n=30 이면 10번째로 작은 값이다. */
    private static BigDecimal lowerBound(List<BigDecimal> sorted) {
        return sorted.get((sorted.size() - 1) / 3);
    }

    /** 상위 1/3 의 첫 값. n=30 이면 21번째로 작은 값이다. */
    private static BigDecimal upperBound(List<BigDecimal> sorted) {
        return sorted.get(sorted.size() - 1 - (sorted.size() - 1) / 3);
    }

    private static List<BigDecimal> sortedRates(List<DailyConcentration> valid) {
        return valid.stream().map(DailyConcentration::rate).sorted().toList();
    }

    private static BigDecimal rateOf(List<DailyConcentration> valid, LocalDate date) {
        return valid.stream()
                .filter(day -> day.date().equals(date))
                .map(DailyConcentration::rate)
                .findFirst()
                .orElse(null);
    }

    /** 지원 범위 안이면서 값이 있는 날만 판정에 쓴다. 범위 밖 날짜가 섞여 분포를 흔들지 않게 한다. */
    private static List<DailyConcentration> validDays(AttractionForecast forecast, LocalDate today) {
        if (forecast == null || forecast.days() == null) {
            return List.of();
        }

        return forecast.days().stream()
                .filter(day -> day.date() != null && day.hasRate())
                .filter(day -> isSupported(day.date(), today))
                .toList();
    }
}
