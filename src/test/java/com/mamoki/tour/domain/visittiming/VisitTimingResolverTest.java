package com.mamoki.tour.domain.visittiming;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.visittiming.dto.AttractionForecast;
import com.mamoki.tour.domain.visittiming.dto.DailyConcentration;
import com.mamoki.tour.domain.visittiming.dto.DailyVisitTiming;
import com.mamoki.tour.domain.visittiming.dto.VisitTimingVerdict;
import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;
import com.mamoki.tour.domain.visittiming.support.ForecastWindow;
import com.mamoki.tour.domain.visittiming.support.VisitTimingResolver;

/**
 * 날짜 확정·유연 탐색의 해석 규칙.
 *
 * <p>외부 호출도 DB 도 타지 않는 순수 계산이라 규칙을 그대로 고정할 수 있다.
 * 슬라이스 #6 의 완료 조건(지원 범위 경계, 장소 내부 상대 수준, 결측·범위 밖 처리,
 * 절대 혼잡도 순위 금지)이 여기서 검증된다.
 *
 * <p>지원 범위는 이제 상수가 아니라 공급자 응답에서 나온 {@link ForecastWindow} 다.
 * 그 창을 어떻게 이끌어 내는지는 {@link ForecastWindowTest} 가 따로 고정한다.
 */
class VisitTimingResolverTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);

    /** 공급자 창의 기준일이 오늘인 날. 지원 범위는 오늘부터 30일이다. */
    private static final ForecastWindow WINDOW = ForecastWindow.nominal(TODAY);

    /** 공급자 창이 하루 뒤처진 날. 오늘 이후로 남는 날이 29일이다. */
    private static final ForecastWindow LAGGED = new ForecastWindow(TODAY, TODAY.plusDays(28));

    // 30일에 1~30 을 순서대로 준 분포. 값으로 삼분위를 끊으면 경계는 10 과 21 이다.
    private static final AttractionForecast ASCENDING = ascendingForecast();

    @Test
    @DisplayName("판정은 공급자가 준 창 안에서만 이뤄진다")
    void judgesInsideProviderWindow() {
        assertThat(WINDOW.contains(TODAY)).isTrue();
        assertThat(WINDOW.contains(TODAY.plusDays(29))).isTrue();
        assertThat(WINDOW.contains(TODAY.minusDays(1))).isFalse();
        assertThat(WINDOW.contains(TODAY.plusDays(30))).isFalse();
    }

    @Test
    @DisplayName("지원 범위의 마지막 날도 예측이 있으면 판정을 받는다")
    void judgesLastDayOfWindow() {
        // 이 이슈(#52)가 고치려는 자리. 예전에는 범위를 오늘+29 로 고정해 두고 공급자 창이
        // 하루 뒤처진 날에는 마지막 날 예측이 없어 늘 NO_DATA 였다.
        AttractionForecast lagged = laggedForecast();

        VisitTimingVerdict verdict =
                VisitTimingResolver.resolveFixed(lagged, LAGGED.to(), LAGGED);

        assertThat(verdict.status()).isEqualTo(VisitTimingStatus.HIGH);
        assertThat(verdict.status()).isNotEqualTo(VisitTimingStatus.NO_DATA);
        assertThat(verdict.selectedDate()).isEqualTo(TODAY.plusDays(28));
    }

    @Test
    @DisplayName("확정 모드: 하위 1/3 은 한산, 상위 1/3 은 혼잡, 나머지는 보통이다")
    void classifiesWithinPlaceDistribution() {
        // 경계를 정확히 짚는다. n=30 이면 10번째로 작은 값까지 LOW, 21번째부터 HIGH.
        assertThat(fixedStatus(0)).isEqualTo(VisitTimingStatus.LOW);
        assertThat(fixedStatus(9)).isEqualTo(VisitTimingStatus.LOW);
        assertThat(fixedStatus(10)).isEqualTo(VisitTimingStatus.NORMAL);
        assertThat(fixedStatus(19)).isEqualTo(VisitTimingStatus.NORMAL);
        assertThat(fixedStatus(20)).isEqualTo(VisitTimingStatus.HIGH);
        assertThat(fixedStatus(29)).isEqualTo(VisitTimingStatus.HIGH);
    }

    @Test
    @DisplayName("확정 모드: 판정에 쓴 유효 예측일 수를 함께 알린다")
    void reportsForecastDays() {
        VisitTimingVerdict verdict =
                VisitTimingResolver.resolveFixed(ASCENDING, TODAY.plusDays(5), WINDOW);

        assertThat(verdict.forecastDays()).isEqualTo(30);
        assertThat(verdict.selectedDate()).isEqualTo(TODAY.plusDays(5));
        assertThat(verdict.quietestDate()).isNull();
    }

    @Test
    @DisplayName("확정 모드: 지원 범위 밖 날짜는 예측을 보기 전에 범위 밖으로 가른다")
    void marksOutOfRange() {
        assertThat(VisitTimingResolver.resolveFixed(ASCENDING, TODAY.minusDays(1), WINDOW).status())
                .isEqualTo(VisitTimingStatus.OUT_OF_RANGE);
        assertThat(VisitTimingResolver.resolveFixed(ASCENDING, TODAY.plusDays(30), WINDOW).status())
                .isEqualTo(VisitTimingStatus.OUT_OF_RANGE);
    }

    @Test
    @DisplayName("확정 모드: 공급자 창이 하루 뒤처지면 그 다음 날부터 범위 밖이다")
    void marksOutOfRangeBeyondLaggedWindow() {
        // 공급자가 다루지 않은 날이다. 정보 없음이 아니라 범위 밖으로 알려야 지원 범위를
        // 함께 안내할 수 있다.
        VisitTimingVerdict verdict =
                VisitTimingResolver.resolveFixed(laggedForecast(), TODAY.plusDays(29), LAGGED);

        assertThat(verdict.status()).isEqualTo(VisitTimingStatus.OUT_OF_RANGE);
    }

    @Test
    @DisplayName("확정 모드: 선택일 예측이 비어 있으면 이웃 값으로 메우지 않는다")
    void doesNotFillMissingSelectedDay() {
        // 범위 안이지만 그 날 값만 비어 있는 경우. 범위 밖(OUT_OF_RANGE)과 구분한다.
        List<DailyConcentration> days = new ArrayList<>(ASCENDING.days());
        days.set(5, new DailyConcentration(TODAY.plusDays(5), null));

        VisitTimingVerdict verdict = VisitTimingResolver.resolveFixed(
                forecastOf(days), TODAY.plusDays(5), WINDOW);

        assertThat(verdict.status()).isEqualTo(VisitTimingStatus.NO_DATA);
        assertThat(verdict.forecastDays()).isEqualTo(29);
    }

    @Test
    @DisplayName("확정 모드: 유효 예측일이 10일 미만이면 분포를 신뢰할 수 없어 판정하지 않는다")
    void requiresMinimumForecastDays() {
        assertThat(VisitTimingResolver.resolveFixed(forecast(9), TODAY, WINDOW).status())
                .isEqualTo(VisitTimingStatus.NO_DATA);
        assertThat(VisitTimingResolver.resolveFixed(forecast(10), TODAY, WINDOW).status())
                .isNotEqualTo(VisitTimingStatus.NO_DATA);
    }

    @Test
    @DisplayName("창이 29일이어도 최소 유효 예측일 기준은 그대로 10일이다")
    void keepsMinimumForecastDaysWhenWindowShrinks() {
        // 창 길이에 따라 최소치를 움직이면 같은 장소가 날마다 다른 이유로 판정되지 않는다.
        assertThat(VisitTimingResolver.resolveFixed(forecast(9), TODAY, LAGGED).status())
                .isEqualTo(VisitTimingStatus.NO_DATA);
        assertThat(VisitTimingResolver.resolveFixed(forecast(10), TODAY, LAGGED).status())
                .isNotEqualTo(VisitTimingStatus.NO_DATA);
    }

    @Test
    @DisplayName("예측이 아예 없으면 값을 만들어내지 않는다")
    void handlesMissingForecast() {
        VisitTimingVerdict fixed = VisitTimingResolver.resolveFixed(null, TODAY, WINDOW);
        VisitTimingVerdict flexible = VisitTimingResolver.resolveFlexible(null, WINDOW);

        assertThat(fixed.status()).isEqualTo(VisitTimingStatus.NO_DATA);
        assertThat(fixed.forecastDays()).isZero();
        assertThat(flexible.status()).isEqualTo(VisitTimingStatus.NO_DATA);
        assertThat(flexible.quietestDate()).isNull();
    }

    @Test
    @DisplayName("범위 밖 날짜가 섞여 있어도 분포에 넣지 않는다")
    void ignoresDaysOutsideWindow() {
        // 지난 날짜에 아주 낮은 값이 있어도 오늘 이후의 등급을 흔들면 안 된다.
        List<DailyConcentration> days = new ArrayList<>();
        days.add(new DailyConcentration(TODAY.minusDays(1), new BigDecimal("-999")));
        days.addAll(ASCENDING.days());

        VisitTimingVerdict verdict = VisitTimingResolver.resolveFixed(
                forecastOf(days), TODAY.plusDays(9), WINDOW);

        assertThat(verdict.forecastDays()).isEqualTo(30);
        assertThat(verdict.status()).isEqualTo(VisitTimingStatus.LOW);
    }

    @Test
    @DisplayName("같은 값은 순번과 무관하게 같은 등급을 받는다")
    void classifiesByValueNotByRank() {
        // 30일 중 25일이 5, 5일이 9. 순번으로 끊으면 같은 5 가 LOW 와 NORMAL 로 갈린다.
        List<DailyConcentration> days = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            days.add(new DailyConcentration(TODAY.plusDays(i), new BigDecimal("5")));
        }
        for (int i = 25; i < 30; i++) {
            days.add(new DailyConcentration(TODAY.plusDays(i), new BigDecimal("9")));
        }

        AttractionForecast forecast = forecastOf(days);

        assertThat(VisitTimingResolver.resolveFixed(forecast, TODAY, WINDOW).status())
                .isEqualTo(VisitTimingStatus.NORMAL);
        assertThat(VisitTimingResolver.resolveFixed(forecast, TODAY.plusDays(24), WINDOW).status())
                .isEqualTo(VisitTimingStatus.NORMAL);
        assertThat(VisitTimingResolver.resolveFixed(forecast, TODAY.plusDays(25), WINDOW).status())
                .isEqualTo(VisitTimingStatus.HIGH);
    }

    @Test
    @DisplayName("유연 모드: 지원 범위 중 가장 한산한 날을 고른다")
    void picksQuietestDay() {
        VisitTimingVerdict verdict = VisitTimingResolver.resolveFlexible(ASCENDING, WINDOW);

        assertThat(verdict.status()).isEqualTo(VisitTimingStatus.LOW);
        assertThat(verdict.quietestDate()).isEqualTo(TODAY);
        assertThat(verdict.selectedDate()).isNull();
        assertThat(verdict.forecastDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("유연 모드: 최솟값이 여럿이면 가장 이른 날을 고른다")
    void prefersEarliestOnTie() {
        List<DailyConcentration> days = new ArrayList<>(ASCENDING.days());
        days.set(3, new DailyConcentration(TODAY.plusDays(3), new BigDecimal("1")));
        days.set(0, new DailyConcentration(TODAY, new BigDecimal("1")));

        VisitTimingVerdict verdict = VisitTimingResolver.resolveFlexible(forecastOf(days), WINDOW);

        assertThat(verdict.quietestDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("유연 모드: 예측이 모두 같으면 없는 한산함을 만들어내지 않는다")
    void refusesToInventQuietDay() {
        List<DailyConcentration> days = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            days.add(new DailyConcentration(TODAY.plusDays(i), new BigDecimal("7")));
        }

        VisitTimingVerdict verdict = VisitTimingResolver.resolveFlexible(forecastOf(days), WINDOW);

        assertThat(verdict.status()).isEqualTo(VisitTimingStatus.NO_DATA);
        assertThat(verdict.quietestDate()).isNull();
    }

    @Test
    @DisplayName("유연 모드: 유효 예측일이 10일 미만이면 판정하지 않는다")
    void flexibleRequiresMinimumForecastDays() {
        assertThat(VisitTimingResolver.resolveFlexible(forecast(9), WINDOW).status())
                .isEqualTo(VisitTimingStatus.NO_DATA);
    }

    @Test
    @DisplayName("서로 다른 장소의 등급은 절대 혼잡도 비교에 쓸 수 없다")
    void gradesAreRelativeToEachPlace() {
        // 붐비는 장소의 한산한 날(집중률 100)이, 한적한 장소의 혼잡한 날(집중률 30)보다 값은 크다.
        // 그래도 각자의 분포 안에서 해석하므로 앞은 LOW, 뒤는 HIGH 가 된다.
        AttractionForecast crowded = linearForecast(100, 3);
        AttractionForecast quiet = linearForecast(1, 1);

        VisitTimingVerdict crowdedDay = VisitTimingResolver.resolveFixed(crowded, TODAY, WINDOW);
        VisitTimingVerdict quietDay =
                VisitTimingResolver.resolveFixed(quiet, TODAY.plusDays(29), WINDOW);

        assertThat(crowdedDay.status()).isEqualTo(VisitTimingStatus.LOW);
        assertThat(quietDay.status()).isEqualTo(VisitTimingStatus.HIGH);
    }

    @Test
    @DisplayName("상세용 일별 판정은 지원 범위를 날짜 오름차순으로 채운다")
    void resolvesEveryDayInSupportedWindow() {
        List<DailyVisitTiming> daily = VisitTimingResolver.resolveDaily(ASCENDING, WINDOW);

        assertThat(daily).hasSize(30);
        assertThat(daily.get(0).date()).isEqualTo(TODAY);
        assertThat(daily.get(29).date()).isEqualTo(TODAY.plusDays(29));
        assertThat(daily).extracting(DailyVisitTiming::date).isSorted();
    }

    @Test
    @DisplayName("일별 판정은 지원 범위가 29일이면 29일만 담는다")
    void dailyFollowsWindowLength() {
        // 상세가 목록보다 하루 더 내려 보내면, 그 하루는 목록에서 범위 밖인 날이 된다.
        List<DailyVisitTiming> daily = VisitTimingResolver.resolveDaily(laggedForecast(), LAGGED);

        assertThat(daily).hasSize(29);
        assertThat(daily.get(0).date()).isEqualTo(TODAY);
        assertThat(daily.get(28).date()).isEqualTo(TODAY.plusDays(28));
        assertThat(daily.get(28).status()).isNotEqualTo(VisitTimingStatus.NO_DATA);
    }

    @Test
    @DisplayName("일별 판정은 확정 모드로 하루씩 물은 것과 같은 결과를 준다")
    void dailyMatchesFixedModeDayByDay() {
        // 상세가 목록과 다른 답을 내면 같은 장소·같은 날에 두 문구가 생긴다.
        List<DailyVisitTiming> daily = VisitTimingResolver.resolveDaily(ASCENDING, WINDOW);

        for (DailyVisitTiming day : daily) {
            assertThat(day.status())
                    .isEqualTo(VisitTimingResolver.resolveFixed(ASCENDING, day.date(), WINDOW).status());
        }
    }

    @Test
    @DisplayName("일별 판정에서 예측이 비어 있는 날은 이웃 값으로 메우지 않는다")
    void leavesMissingDayAsNoData() {
        List<DailyConcentration> days = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            // 5일째만 값이 없다. 앞뒤가 있어도 그 날을 만들어 내지 않는다.
            days.add(new DailyConcentration(TODAY.plusDays(i),
                    i == 5 ? null : BigDecimal.valueOf(i + 1L)));
        }

        List<DailyVisitTiming> daily = VisitTimingResolver.resolveDaily(forecastOf(days), WINDOW);

        assertThat(daily.get(5).status()).isEqualTo(VisitTimingStatus.NO_DATA);
        assertThat(daily.get(4).status()).isNotEqualTo(VisitTimingStatus.NO_DATA);
    }

    @Test
    @DisplayName("유효 예측일이 10일 미만이면 지원 범위 전체를 판정하지 않는다")
    void marksEveryDayNoDataWhenTooFewForecasts() {
        List<DailyVisitTiming> daily = VisitTimingResolver.resolveDaily(forecast(9), WINDOW);

        assertThat(daily).hasSize(30);
        assertThat(daily).extracting(DailyVisitTiming::status)
                .containsOnly(VisitTimingStatus.NO_DATA);
    }

    @Test
    @DisplayName("예측이 아예 없어도 지원 범위의 자리를 만들고 모두 정보 없음으로 남긴다")
    void keepsWindowWhenForecastAbsent() {
        List<DailyVisitTiming> daily = VisitTimingResolver.resolveDaily(null, WINDOW);

        assertThat(daily).hasSize(30);
        assertThat(daily).extracting(DailyVisitTiming::status)
                .containsOnly(VisitTimingStatus.NO_DATA);
    }

    @Test
    @DisplayName("일별 판정 계약에 집중률 원본값이 들어가지 않는다")
    void dailyNeverExposesRawRate() {
        // 30일을 한 번에 내려주므로 값이 있으면 장소 사이의 절대 비교가 더 쉬워진다.
        List<String> components = java.util.Arrays.stream(DailyVisitTiming.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(components).containsExactly("date", "status");
    }

    private VisitTimingStatus fixedStatus(int dayOffset) {
        return VisitTimingResolver.resolveFixed(ASCENDING, TODAY.plusDays(dayOffset), WINDOW).status();
    }

    private static AttractionForecast ascendingForecast() {
        return linearForecast(1, 1);
    }

    /** 오늘부터 30일간 {@code start} 에서 {@code step} 씩 증가하는 예측. */
    private static AttractionForecast linearForecast(int start, int step) {
        List<DailyConcentration> days = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            days.add(new DailyConcentration(
                    TODAY.plusDays(i), BigDecimal.valueOf(start + (long) i * step)));
        }

        return forecastOf(days);
    }

    /** 기준일이 어제인 30일 예측. 오늘 이후로는 29일이 남는다. */
    private static AttractionForecast laggedForecast() {
        List<DailyConcentration> days = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            days.add(new DailyConcentration(
                    TODAY.minusDays(1).plusDays(i), BigDecimal.valueOf(i + 1L)));
        }

        return forecastOf(days);
    }

    /** 오늘부터 {@code dayCount} 일치만 있는 예측. */
    private static AttractionForecast forecast(int dayCount) {
        List<DailyConcentration> days = new ArrayList<>();

        for (int i = 0; i < dayCount; i++) {
            days.add(new DailyConcentration(TODAY.plusDays(i), BigDecimal.valueOf(i + 1L)));
        }

        return forecastOf(days);
    }

    private static AttractionForecast forecastOf(List<DailyConcentration> days) {
        return new AttractionForecast("테스트장소", "테스트장소", "51150", null, null, days);
    }
}
