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
import com.mamoki.tour.domain.visittiming.support.ForecastWindow;

/**
 * 지원 범위를 공급자 응답에서 이끌어 내는 규칙.
 *
 * <p>실호출로 확인한 사실은 두 가지다. 공급자는 <b>연속한 30일</b>을 주고, 그 기준일은
 * 조회한 날일 수도 있고 하루 전일 수도 있다.
 *
 * <ul>
 *   <li>2026-09-08 에 부른 강릉시 응답: {@code 20260907 ~ 20261006} (하루 뒤처짐)</li>
 *   <li>2026-09-19 에 부른 강릉시 응답: {@code 20260919 ~ 20261018} (조회일과 같음)</li>
 * </ul>
 *
 * <p>그래서 "오늘부터 30일" 같은 고정 상수로는 어느 날엔가 반드시 어긋난다. 범위는 응답에
 * 실제로 들어 있는 날짜에서 이끌어 낸다.
 */
class ForecastWindowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);

    @Test
    @DisplayName("공급자가 준 날짜 창을 그대로 지원 범위로 삼는다")
    void followsProviderWindow() {
        ForecastWindow window = ForecastWindow.of(List.of(forecast(TODAY, 30)), TODAY);

        assertThat(window.from()).isEqualTo(TODAY);
        assertThat(window.to()).isEqualTo(TODAY.plusDays(29));
        assertThat(window.days()).isEqualTo(30);
    }

    @Test
    @DisplayName("공급자 창이 하루 뒤처지면 지원 범위도 하루 짧아진다")
    void shrinksWhenProviderWindowLagsByOneDay() {
        // 기준일이 어제인 30일 창. 오늘 이후로 남는 날은 29일이다.
        ForecastWindow window = ForecastWindow.of(List.of(forecast(TODAY.minusDays(1), 30)), TODAY);

        assertThat(window.from()).isEqualTo(TODAY);
        assertThat(window.to()).isEqualTo(TODAY.plusDays(28));
        assertThat(window.days()).isEqualTo(29);

        // 이 이슈가 고치려는 자리. 예전에는 +29 일을 지원 범위라고 안내하고 늘 정보 없음을 줬다.
        assertThat(window.contains(TODAY.plusDays(28))).isTrue();
        assertThat(window.contains(TODAY.plusDays(29))).isFalse();
    }

    @Test
    @DisplayName("지난 날짜는 지원 범위에 넣지 않는다")
    void excludesPastDays() {
        ForecastWindow window = ForecastWindow.of(List.of(forecast(TODAY.minusDays(1), 30)), TODAY);

        assertThat(window.contains(TODAY.minusDays(1))).isFalse();
        assertThat(window.contains(TODAY)).isTrue();
    }

    @Test
    @DisplayName("시·군 안에서 날짜가 빠진 장소가 있어도 창은 그 시·군 전체를 본다")
    void takesWindowFromWholeRegion() {
        // 페이지 경계에서 잘려 10일치만 들어온 장소가 섞여도 지원 범위는 줄지 않는다.
        // 그 장소의 빠진 날은 범위 밖이 아니라 값이 없는 날(NO_DATA)이다.
        ForecastWindow window = ForecastWindow.of(
                List.of(forecast(TODAY, 30), forecast(TODAY, 10)), TODAY);

        assertThat(window.to()).isEqualTo(TODAY.plusDays(29));
    }

    @Test
    @DisplayName("예측을 얻지 못하면 있을 수 있는 가장 넓은 범위를 안내한다")
    void fallsBackToNominalWindow() {
        // 창을 확인할 수 없을 때 범위를 좁혀 잡으면, 실제로는 답할 수 있는 날을 범위 밖이라고
        // 잘라내게 된다. 공급자 기준일은 조회일을 앞서지 않으므로 오늘부터 30일이 상한이다.
        ForecastWindow window = ForecastWindow.of(List.of(), TODAY);

        assertThat(window).isEqualTo(ForecastWindow.nominal(TODAY));
        assertThat(window.from()).isEqualTo(TODAY);
        assertThat(window.to()).isEqualTo(TODAY.plusDays(29));
    }

    @Test
    @DisplayName("창이 통째로 지난 날짜면 기본 범위로 돌아간다")
    void fallsBackWhenWindowIsEntirelyInThePast() {
        ForecastWindow window = ForecastWindow.of(
                List.of(forecast(TODAY.minusDays(40), 30)), TODAY);

        assertThat(window).isEqualTo(ForecastWindow.nominal(TODAY));
    }

    @Test
    @DisplayName("날짜가 없는 행은 창을 흔들지 않는다")
    void ignoresRowsWithoutDate() {
        List<DailyConcentration> days = new ArrayList<>();
        days.add(new DailyConcentration(null, new BigDecimal("1")));
        days.addAll(forecast(TODAY, 30).days());

        ForecastWindow window = ForecastWindow.of(
                List.of(new AttractionForecast("장소", "장소", "51150", null, null, days)), TODAY);

        assertThat(window.from()).isEqualTo(TODAY);
        assertThat(window.to()).isEqualTo(TODAY.plusDays(29));
    }

    @Test
    @DisplayName("범위의 날짜를 오름차순으로 하루도 빠짐없이 펼친다")
    void listsEveryDayAscending() {
        ForecastWindow window = ForecastWindow.of(List.of(forecast(TODAY.minusDays(1), 30)), TODAY);

        List<LocalDate> dates = window.dates();

        assertThat(dates).hasSize(window.days());
        assertThat(dates.get(0)).isEqualTo(window.from());
        assertThat(dates.get(dates.size() - 1)).isEqualTo(window.to());
        assertThat(dates).isSorted();
    }

    /** {@code baseDate} 부터 {@code dayCount} 일치 예측을 가진 장소 하나. */
    private static AttractionForecast forecast(LocalDate baseDate, int dayCount) {
        List<DailyConcentration> days = new ArrayList<>();

        for (int i = 0; i < dayCount; i++) {
            days.add(new DailyConcentration(baseDate.plusDays(i), BigDecimal.valueOf(i + 1L)));
        }

        return new AttractionForecast("장소" + dayCount, "장소" + dayCount, "51150", null, null, days);
    }
}
