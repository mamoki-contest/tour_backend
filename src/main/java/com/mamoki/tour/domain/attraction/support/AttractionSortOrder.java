package com.mamoki.tour.domain.attraction.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSort;

/**
 * 온라인 언급량 정렬 규칙.
 *
 * <p>산정된 장소끼리만 정렬하고, 값이 없는 장소는 뒤에 붙인다. 미산정을 낮은 값으로 취급하면
 * 언급 적은 순 상단이 전부 정보 없음으로 채워져, 정작 언급이 적은 장소를 볼 수 없게 된다.
 *
 * <p>동률은 표준 관광지명 오름차순으로 고정한다. 같은 값이 여럿일 때 호출마다 순서가 달라지면
 * 페이지를 넘길 때 항목이 중복되거나 빠진다.
 */
public class AttractionSortOrder {

    private static final Comparator<AttractionResponse> BY_NAME =
            Comparator.comparing(AttractionResponse::name, Comparator.nullsLast(String::compareTo));

    public List<AttractionResponse> order(List<AttractionResponse> items, AttractionSort sort) {
        Comparator<AttractionResponse> byCount =
                Comparator.comparing(item -> item.onlineMention().count());

        Comparator<AttractionResponse> sortable =
                (sort.isAscending() ? byCount : byCount.reversed()).thenComparing(BY_NAME);

        List<AttractionResponse> ordered = new ArrayList<>(items.size());
        ordered.addAll(items.stream().filter(AttractionSortOrder::isSortable).sorted(sortable).toList());
        ordered.addAll(items.stream().filter(item -> !isSortable(item)).sorted(BY_NAME).toList());

        return ordered;
    }

    private static boolean isSortable(AttractionResponse item) {
        return item.onlineMention() != null && item.onlineMention().isSortable();
    }
}
