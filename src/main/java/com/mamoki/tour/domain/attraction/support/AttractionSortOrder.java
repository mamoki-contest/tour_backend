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
 *
 * <p><b>산정된 장소가 하나도 없으면 정렬하지 않는다(#65).</b> 그때는 줄을 세울 기준 자체가
 * 없어서, 값 없는 장소끼리의 이름순이 남는다. 그 순서는 요청한 정렬과 아무 상관이 없는데도
 * 응답의 {@code sort} 는 요청한 기준을 그대로 실어, 가나다순 목록이 언급량 순으로 읽혔다.
 * 순서를 만들어내는 대신 들어온 순서를 그대로 두고, 적용하지 못했다는 사실을 함께 돌려준다.
 */
public class AttractionSortOrder {

    private static final Comparator<AttractionResponse> BY_NAME =
            Comparator.comparing(AttractionResponse::name, Comparator.nullsLast(String::compareTo));

    public Ordered order(List<AttractionResponse> items, AttractionSort sort) {
        List<AttractionResponse> sortable = items.stream().filter(AttractionSortOrder::isSortable).toList();

        if (sortable.isEmpty()) {
            return Ordered.notApplied(items);
        }

        Comparator<AttractionResponse> byCount =
                Comparator.comparing(item -> item.onlineMention().count());

        Comparator<AttractionResponse> byMention =
                (sort.isAscending() ? byCount : byCount.reversed()).thenComparing(BY_NAME);

        List<AttractionResponse> ordered = new ArrayList<>(items.size());
        ordered.addAll(sortable.stream().sorted(byMention).toList());
        ordered.addAll(items.stream().filter(item -> !isSortable(item)).sorted(BY_NAME).toList());

        return new Ordered(ordered, true);
    }

    private static boolean isSortable(AttractionResponse item) {
        return item.onlineMention() != null && item.onlineMention().isSortable();
    }

    /**
     * 정렬 결과와, 요청한 정렬을 실제로 적용했는지.
     *
     * @param applied 산정된 장소가 하나도 없어 줄을 세우지 못했으면 false. 그때 {@code items}
     *                는 들어온 순서 그대로다.
     */
    public record Ordered(List<AttractionResponse> items, boolean applied) {

        /** 정렬 기준이 없거나 정렬을 요청하지 않았을 때. 들어온 순서를 그대로 쓴다. */
        public static Ordered notApplied(List<AttractionResponse> items) {
            return new Ordered(items, false);
        }
    }
}
