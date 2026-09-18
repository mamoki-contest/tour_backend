package com.mamoki.tour.infra.its;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mamoki.tour.domain.currentaccess.dto.RoadFlowView;
import com.mamoki.tour.domain.currentaccess.dto.RoadFlowView.RoadSegmentView;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.infra.its.dto.ItsItem;

/**
 * 링크 구간들을 도로명별 요약으로 묶는다.
 *
 * <p>구간을 그대로 내려주지 않는다. 좁은 사각형에서도 수십 개, 시내에서는 천 개가 넘게 온다.
 * 링크 식별자는 화면에 쓸 수 없는 값이라 도로명으로 묶어 평균을 낸다.
 *
 * <p>속도를 등급으로 바꾸지 않는다. 제한속도를 알 수 없어 낮은 속도가 정체인지 원래 그런
 * 도로인지 판단할 근거가 없다.
 */
public final class ItsItemConverter {

    public static final String SOURCE = "국가교통정보센터";

    /** 응답에 담을 도로 수. 관측 구간이 많은 쪽부터 담는다. */
    private static final int MAX_ROADS = 5;

    private static final DateTimeFormatter CREATED_DATE =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private ItsItemConverter() {
    }

    public static RoadFlowView convert(List<ItsItem> items) {
        List<ItsItem> usable = items.stream()
                .filter(item -> toDouble(item.speed()) != null)
                .toList();

        if (usable.isEmpty()) {
            return RoadFlowView.noData();
        }

        double averageSpeed = usable.stream()
                .mapToDouble(item -> toDouble(item.speed()))
                .average()
                .orElseThrow();

        return new RoadFlowView(
                DataStatus.AVAILABLE,
                usable.size(),
                round(averageSpeed),
                summarizeByRoad(usable),
                observedAt(usable));
    }

    /** 도로명이 없는 구간은 평균에는 넣되 목록에는 담지 않는다. 화면에 이름 없이 줄을 만들 수 없다. */
    private static List<RoadSegmentView> summarizeByRoad(List<ItsItem> items) {
        Map<String, List<ItsItem>> byRoad = new LinkedHashMap<>();

        for (ItsItem item : items) {
            String roadName = blankToNull(item.roadName());

            if (roadName != null) {
                byRoad.computeIfAbsent(roadName, key -> new ArrayList<>()).add(item);
            }
        }

        List<RoadSegmentView> roads = new ArrayList<>(byRoad.size());

        for (Map.Entry<String, List<ItsItem>> entry : byRoad.entrySet()) {
            List<ItsItem> links = entry.getValue();

            roads.add(new RoadSegmentView(
                    entry.getKey(),
                    links.size(),
                    round(links.stream().mapToDouble(item -> toDouble(item.speed())).average().orElse(0)),
                    round(links.stream()
                            .map(item -> toDouble(item.travelTime()))
                            .filter(java.util.Objects::nonNull)
                            .mapToDouble(Double::doubleValue)
                            .average()
                            .orElse(0))));
        }

        return roads.stream()
                .sorted(Comparator.comparingInt(RoadSegmentView::linkCount).reversed()
                        .thenComparing(RoadSegmentView::roadName))
                .limit(MAX_ROADS)
                .toList();
    }

    /** 구간마다 생성시각이 같게 오지만, 다르면 가장 최근 것을 기준으로 삼는다. */
    private static LocalDateTime observedAt(List<ItsItem> items) {
        return items.stream()
                .map(item -> toDateTime(item.createdDate()))
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static LocalDateTime toDateTime(String value) {
        String raw = blankToNull(value);

        if (raw == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(raw, CREATED_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Double toDouble(String value) {
        String raw = blankToNull(value);

        if (raw == null) {
            return null;
        }

        try {
            return Double.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
