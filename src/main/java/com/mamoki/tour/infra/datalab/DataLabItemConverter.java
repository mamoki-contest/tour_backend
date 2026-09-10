package com.mamoki.tour.infra.datalab;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mamoki.tour.domain.region.dto.RegionVisitors;
import com.mamoki.tour.infra.datalab.dto.DataLabItem;

/**
 * DataLab 일별 항목을 시·군 방문 규모로 집계한다.
 *
 * <p>현지인(touDivCd=1)은 담지 않는다. 거주 인구가 그대로 잡히는 값이라 함께 더하면
 * 인구가 많은 시가 늘 위로 올라가고 방문 규모를 비교할 수 없게 된다.
 *
 * <p>값이 하나도 없는 시·군은 0 으로 만들지 않고 결과에서 뺀다. 0 명 방문과
 * 수집 실패를 구분해야 하기 때문이다. 판단은 호출부가 한다.
 */
public final class DataLabItemConverter {

    public static final String SOURCE = "한국관광공사 빅데이터 지역별 방문자수";

    /** 관광객 구분. 현지인은 거주 인구라 방문 규모에서 뺀다. */
    private static final String TOU_DIV_LOCAL = "1";

    private DataLabItemConverter() {
    }

    /**
     * 여러 날·여러 구분으로 흩어진 행을 시·군 단위 합계로 묶는다.
     *
     * <p>같은 시·군의 여러 페이지를 이어 붙여도 되도록 입력 순서에 기대지 않는다.
     */
    public static List<RegionVisitors> convertAll(List<DataLabItem> items) {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, Set<String>> days = new LinkedHashMap<>();

        for (DataLabItem item : items) {
            String lawdCode = blankToNull(item.signguCode());
            BigDecimal visitors = toDecimal(item.touNum());

            if (lawdCode == null || visitors == null || TOU_DIV_LOCAL.equals(item.touDivCd())) {
                continue;
            }

            names.putIfAbsent(lawdCode, blankToNull(item.signguNm()));
            totals.merge(lawdCode, visitors, BigDecimal::add);

            String baseYmd = blankToNull(item.baseYmd());
            if (baseYmd != null) {
                days.computeIfAbsent(lawdCode, key -> new HashSet<>()).add(baseYmd);
            }
        }

        List<RegionVisitors> aggregated = new ArrayList<>(totals.size());
        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            String lawdCode = entry.getKey();

            aggregated.add(new RegionVisitors(
                    lawdCode,
                    names.get(lawdCode),
                    entry.getValue().setScale(0, RoundingMode.HALF_UP).longValue(),
                    days.getOrDefault(lawdCode, Set.of()).size()));
        }

        return aggregated;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 방문자 수는 소수점이 붙은 추정치 문자열로 온다. 해석되지 않으면 0 으로 만들지 않고 버린다. */
    private static BigDecimal toDecimal(String value) {
        String number = blankToNull(value);

        if (number == null) {
            return null;
        }

        try {
            return new BigDecimal(number);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
