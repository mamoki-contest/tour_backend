package com.mamoki.tour.domain.visittiming.support;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mamoki.tour.domain.visittiming.dto.AttractionForecast;
import com.mamoki.tour.domain.visittiming.dto.DailyConcentration;

/**
 * 날짜 탐색이 답할 수 있는 날짜 구간. 목록과 상세가 함께 쓰는 <b>하나뿐인 범위 정의</b>다.
 *
 * <p>범위를 상수로 박지 않고 공급자 응답에서 이끌어 낸다. 실호출로 확인한 사실은 두 가지다.
 *
 * <ul>
 *   <li>공급자는 한 번에 <b>연속한 30일</b>을 준다.</li>
 *   <li>그 창의 기준일은 조회한 날일 수도 있고 하루 전일 수도 있다.
 *       2026-09-08 에 부른 강릉시(51150) 응답은 {@code 20260907 ~ 20261006} 이었고,
 *       2026-09-19 에 부른 같은 응답은 {@code 20260919 ~ 20261018} 이었다.</li>
 * </ul>
 *
 * <p>"오늘부터 30일" 로 고정하면 창이 하루 뒤처진 날에는 마지막 날에 줄 수 있는 답이 없어
 * 늘 {@code NO_DATA} 가 된다. 반대로 "내일부터 30일" 로 고정하면 창이 뒤처지지 않은 날에
 * 오늘을 잘라내게 된다. 어느 쪽도 맞지 않으므로 응답에 실제로 들어 있는 날짜를 따른다.
 *
 * @param from 지원 시작일. 지난 날짜는 담지 않으므로 오늘보다 앞서지 않는다.
 * @param to   지원 종료일. 공급자 창의 마지막 날이다.
 */
public record ForecastWindow(LocalDate from, LocalDate to) {

    private static final Logger log = LoggerFactory.getLogger(ForecastWindow.class);

    /**
     * 공급자가 한 번에 주는 예측 일수. 창을 확인하지 못했을 때 안내할 범위의 길이로만 쓴다.
     *
     * <p>공급자 기준일이 조회일을 앞서는 경우는 없었으므로, 이 길이가 지원 범위의 상한이다.
     */
    public static final int NOMINAL_DAYS = 30;

    public ForecastWindow {
        if (from == null || to == null) {
            throw new IllegalArgumentException("지원 범위의 시작일과 종료일은 비어 있을 수 없습니다.");
        }

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("지원 범위의 종료일이 시작일보다 앞설 수 없습니다.");
        }
    }

    /**
     * 창을 확인하지 못했을 때 안내할 기본 범위. 오늘부터 {@link #NOMINAL_DAYS} 일이다.
     *
     * <p>좁혀 잡으면 실제로는 답할 수 있는 날을 범위 밖이라고 잘라내게 되므로, 있을 수 있는
     * 가장 넓은 범위를 안내한다. 이 범위 안이어도 예측이 없으면 그 날은 {@code NO_DATA} 다.
     */
    public static ForecastWindow nominal(LocalDate today) {
        return new ForecastWindow(today, today.plusDays(NOMINAL_DAYS - 1L));
    }

    /**
     * 시·군 하나의 예측에서 지원 범위를 이끌어 낸다.
     *
     * <p>장소 하나가 아니라 <b>시·군 전체</b>의 날짜를 본다. 페이지 경계에서 잘려 며칠만 들어온
     * 장소가 있어도 창은 줄지 않는다. 그 장소의 빠진 날은 범위 밖이 아니라 값이 없는 날이다.
     *
     * <p>값이 비어 있는 날도 창에는 넣는다. 공급자가 그 날을 다뤘다는 사실과, 그 날 값을
     * 주지 않았다는 사실은 다른 이야기다.
     *
     * @param forecasts 시·군 하나의 장소별 예측. 비어 있으면 {@link #nominal(LocalDate)} 로 돌아간다.
     */
    public static ForecastWindow of(Collection<AttractionForecast> forecasts, LocalDate today) {
        LocalDate first = null;
        LocalDate last = null;

        for (AttractionForecast forecast : forecasts) {
            if (forecast == null || forecast.days() == null) {
                continue;
            }

            for (DailyConcentration day : forecast.days()) {
                LocalDate date = day == null ? null : day.date();

                if (date == null) {
                    continue;
                }

                first = first == null || date.isBefore(first) ? date : first;
                last = last == null || date.isAfter(last) ? date : last;
            }
        }

        if (first == null) {
            return nominal(today);
        }

        // 공급자가 한 번에 주는 것은 연속한 30일이고 기준일이 조회일을 앞선 적은 없다.
        // 그보다 뒤인 날짜가 한 줄이라도 섞여 들어오면 응답이 이상한 것이지 범위가 늘어난
        // 것이 아니다. 그대로 따르면 그 한 줄 때문에 지원 범위가 몇 달로 벌어지고, 판정할
        // 값이 없는 날이 수십 개 붙는다. 상한을 넘으면 잘라내고 무슨 일이 있었는지 남긴다.
        LocalDate limit = today.plusDays(NOMINAL_DAYS - 1L);

        if (last.isAfter(limit)) {
            log.warn("공급자 예측에 지원 범위를 넘는 날짜가 섞여 있어 잘라냅니다: 응답 마지막={}, 상한={}",
                    last, limit);
            last = limit;
        }

        LocalDate start = first.isBefore(today) ? today : first;

        // 창이 통째로 지나갔으면 안내할 수 있는 범위가 없다. 빈 범위를 만들어 모든 날을
        // 범위 밖이라고 말하는 대신 기본 범위로 돌아간다.
        if (last.isBefore(start)) {
            return nominal(today);
        }

        return new ForecastWindow(start, last);
    }

    public boolean contains(LocalDate date) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    /** 지원 범위의 날 수. 공급자 창이 하루 뒤처진 날에는 30일이 아니라 29일이다. */
    public int days() {
        return (int) (to.toEpochDay() - from.toEpochDay()) + 1;
    }

    /** 지원 범위의 모든 날짜를 오름차순으로 펼친다. */
    public List<LocalDate> dates() {
        List<LocalDate> dates = new ArrayList<>(days());

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            dates.add(date);
        }

        return List.copyOf(dates);
    }
}
