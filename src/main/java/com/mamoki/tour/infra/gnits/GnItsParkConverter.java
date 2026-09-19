package com.mamoki.tour.infra.gnits;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mamoki.tour.infra.gnits.dto.GnParkInfoItem;
import com.mamoki.tour.infra.gnits.dto.GnParkRltmItem;
import com.mamoki.tour.infra.gnits.dto.RealtimeParkingLot;

/**
 * 기본정보와 실시간 현황을 {@code prkId} 로 잇는다.
 *
 * <p>기준은 기본정보다. 좌표가 거기에만 있고, 좌표가 없으면 어느 관광지 주변인지 말할 수
 * 없어 쓸 데가 없다. 실시간에만 있고 기본정보에 없는 주차장은 담지 않는다.
 *
 * <p>숫자가 전부 문자열로 오므로 여기서 한 번만 숫자로 바꾼다. 해석되지 않는 값은 0 이
 * 아니라 null 이다. 0 은 "만차" 라는 실제 값이어서 결측과 섞이면 안 된다.
 */
public final class GnItsParkConverter {

    private GnItsParkConverter() {
    }

    public static List<RealtimeParkingLot> join(List<GnParkInfoItem> info,
                                                List<GnParkRltmItem> realtime) {

        Map<String, GnParkRltmItem> byId = new LinkedHashMap<>();

        for (GnParkRltmItem item : realtime) {
            String id = blankToNull(item.prkId());

            if (id != null) {
                byId.putIfAbsent(id, item);
            }
        }

        List<RealtimeParkingLot> lots = new ArrayList<>(info.size());

        for (GnParkInfoItem item : info) {
            String id = blankToNull(item.prkId());
            String name = blankToNull(item.prkName());

            // 식별자나 이름이 없으면 어느 주차장인지 말할 수 없다.
            if (id == null || name == null) {
                continue;
            }

            GnParkRltmItem counts = byId.get(id);

            lots.add(new RealtimeParkingLot(
                    id,
                    name,
                    blankToNull(item.prkAddr()),
                    // yCrdn 이 위도, xCrdn 이 경도다. 바꿔 읽으면 서해 밖에 찍힌다.
                    toDecimal(item.yCrdn()),
                    toDecimal(item.xCrdn()),
                    blankToNull(item.prkType()),
                    counts == null ? null : toInteger(counts.totalLots()),
                    counts == null ? null : toInteger(counts.occupiedLots()),
                    blankToNull(item.weekOpenTime()),
                    blankToNull(item.weekEndTime()),
                    blankToNull(item.satOpenTime()),
                    blankToNull(item.satEndTime()),
                    blankToNull(item.holiOpenTime()),
                    blankToNull(item.holiEndTime())));
        }

        return lots;
    }

    private static Integer toInteger(String value) {
        String raw = blankToNull(value);

        if (raw == null) {
            return null;
        }

        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toDecimal(String value) {
        String raw = blankToNull(value);

        if (raw == null) {
            return null;
        }

        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
