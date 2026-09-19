package com.mamoki.tour.domain.visittiming.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mamoki.tour.domain.visittiming.dto.AttractionForecast;
import com.mamoki.tour.domain.visittiming.dto.DailyConcentration;
import com.mamoki.tour.domain.visittiming.dto.DailyVisitTiming;
import com.mamoki.tour.domain.visittiming.dto.VisitTimingVerdict;
import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;

/**
 * 집중률 예측을 장소 내부 상대 수준으로 해석하는 규칙.
 *
 * <p>공급자는 집중률의 공식 등급 기준을 제공하지 않는다. 그래서 값 자체를 한산·보통·혼잡으로
 * 끊을 근거가 없다. 대신 <b>그 장소의 지원 범위 안 분포</b>에서 어디쯤인지만 본다.
 * 이렇게 하면 어떤 장소의 HIGH 와 다른 장소의 HIGH 를 비교할 수 없게 되는데, PRD 가
 * 금지한 절대 혼잡도 순위를 애초에 만들 수 없게 하려는 것이 목적이다.
 *
 * <p>판정할 날짜의 범위는 여기서 정하지 않는다. 공급자 응답에서 나온 {@link ForecastWindow}
 * 를 받아 쓴다. 목록과 상세가 같은 창을 넘겨받으므로 두 화면의 판정이 어긋나지 않는다.
 *
 * <p>규칙은 다음 세 개다. 임의로 바꾸면 프론트 표시 문구와 어긋나므로 상수로 고정한다.
 * <ol>
 *   <li>정렬한 유효 예측의 하위 1/3 은 {@code LOW}, 상위 1/3 은 {@code HIGH}, 나머지는 {@code NORMAL}.
 *       경계값이 같으면 같은 등급이 되도록 순번이 아니라 값으로 끊는다.</li>
 *   <li>유효 예측일이 {@link #MIN_FORECAST_DAYS_FOR_LEVEL} 일 미만이면 판정하지 않고 {@code NO_DATA}.
 *       한 달 창의 1/3 도 모이지 않은 분포로 상대 수준을 말할 수 없다.</li>
 *   <li>같은 값이 분포의 대부분을 덮어 두 경계가 붙으면, 그 값은 이 장소의 보통({@code NORMAL})이고
 *       그보다 낮은 날만 {@code LOW}, 높은 날만 {@code HIGH} 다.
 *       유연 모드는 예측이 모두 같을 때만 {@code NO_DATA} 다.
 *       더 한산한 날이 없는데 하나를 골라 주면 없는 한산함을 만들어내는 것이다.</li>
 * </ol>
 */
public final class VisitTimingResolver {

    /**
     * 이보다 적은 유효 예측일로는 분포를 신뢰할 수 없어 판정하지 않는다. 30일의 1/3.
     *
     * <p>지원 범위가 29일로 줄어도 이 기준은 그대로 둔다. 창 길이에 따라 최소치를 움직이면
     * 같은 장소가 날마다 다른 이유로 판정되거나 되지 않는다.
     */
    public static final int MIN_FORECAST_DAYS_FOR_LEVEL = 10;

    private VisitTimingResolver() {
    }

    /**
     * 날짜 확정 모드. 선택일을 그 장소 자신의 분포 안에서 해석한다.
     *
     * @param forecast 이 장소의 예측. 매칭되지 않았으면 null 이며 정보 없음으로 처리한다.
     * @param window   공급자 응답에서 나온 지원 범위
     */
    public static VisitTimingVerdict resolveFixed(AttractionForecast forecast,
                                                  LocalDate selectedDate, ForecastWindow window) {

        // 범위 밖은 데이터 문제가 아니라 요청 문제다. 예측을 보기 전에 먼저 가른다.
        if (!window.contains(selectedDate)) {
            return VisitTimingVerdict.outOfRange(selectedDate, 0);
        }

        List<DailyConcentration> valid = validDays(forecast, window);

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

    /** 날짜 유연 모드. 그 장소의 지원 범위 안에서 상대적으로 한산한 예상일을 고른다. */
    public static VisitTimingVerdict resolveFlexible(AttractionForecast forecast,
                                                     ForecastWindow window) {

        List<DailyConcentration> valid = validDays(forecast, window);

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
     * 상세 화면용. 지원 범위를 하루씩 모두 판정한다.
     *
     * <p>확정 모드를 하루씩 부른 것과 같은 결과가 나오도록 같은 창·같은 분포·같은 경계를 쓴다.
     * 날짜마다 따로 계산하면 분포가 달라져 목록 응답과 어긋날 수 있다.
     *
     * <p>예측이 없는 날은 이웃 값으로 메우지 않고 {@code NO_DATA} 로 남긴다. 유효 예측일이
     * 판정 최소치에 못 미치면 범위 전체가 {@code NO_DATA} 다.
     *
     * @return 날짜 오름차순. 지원 범위 밖은 담지 않으므로 창이 29일이면 29일이다.
     */
    public static List<DailyVisitTiming> resolveDaily(AttractionForecast forecast,
                                                      ForecastWindow window) {

        List<DailyConcentration> valid = validDays(forecast, window);
        boolean judgeable = valid.size() >= MIN_FORECAST_DAYS_FOR_LEVEL;
        List<BigDecimal> sorted = judgeable ? sortedRates(valid) : List.of();

        List<DailyVisitTiming> daily = new ArrayList<>(window.days());

        for (LocalDate date : window.dates()) {
            BigDecimal rate = judgeable ? rateOf(valid, date) : null;

            daily.add(new DailyVisitTiming(date,
                    rate == null ? VisitTimingStatus.NO_DATA : classify(rate, sorted)));
        }

        return List.copyOf(daily);
    }

    /**
     * 값으로 삼분위를 끊는다. 순번으로 끊으면 값이 같은 날이 서로 다른 등급을 받는다.
     *
     * <p>{@code sorted} 는 오름차순 유효 예측값이며 비어 있지 않다.
     */
    private static VisitTimingStatus classify(BigDecimal rate, List<BigDecimal> sorted) {
        BigDecimal lower = lowerBound(sorted);
        BigDecimal upper = upperBound(sorted);

        // 같은 값이 창의 3분의 2를 덮으면 두 경계가 붙는다. 이때 그 값은 이 장소의 보통이다.
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
    private static List<DailyConcentration> validDays(AttractionForecast forecast,
                                                      ForecastWindow window) {

        if (forecast == null || forecast.days() == null) {
            return List.of();
        }

        return forecast.days().stream()
                .filter(day -> day.date() != null && day.hasRate())
                .filter(day -> window.contains(day.date()))
                .toList();
    }
}
