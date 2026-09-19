package com.mamoki.tour.domain.currentaccess.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mamoki.tour.domain.currentaccess.enums.ParkingStatus;
import com.mamoki.tour.global.enums.DataStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관광지 주변 주차 여건.
 *
 * <p>두 공급자를 겹쳐 쓴다. 실시간 잔여면은 강릉시 교통정보 조회서비스에서, 주차장이
 * 거기 있는지와 규모는 전국주차장정보표준데이터에서 온다. 실시간은 강릉시 13곳뿐이고
 * 표준데이터는 강원 18개 시·군 1,398곳이라 서로를 대신하지 못한다.
 *
 * <p><b>{@code status} 와 {@code dataStatus} 는 다른 것을 말한다.</b> {@code status} 는
 * 주차장이 있느냐·실시간을 아느냐이고, {@code dataStatus} 는 그 실시간 값이 방금 받은
 * 것이냐 최종 정상 데이터냐다. 실시간 공급자가 잠시 막혀 최종 정상 데이터로 응답할 때
 * {@code status=AVAILABLE} 이면서 {@code dataStatus=STALE} 이 된다.
 *
 * @param observedAt 실시간 값을 받은 시각. 공급자에 기준시각 필드가 없어 우리 수신 시각이다
 */
@Schema(description = """
        관광지 주변 주차 여건. 정보 없음을 '주차 자리 없음' 으로 표시하지 마세요.
        status 로 '주차장이 없음'·'주차장은 있으나 실시간 모름'·'확인 못 함'을 구분합니다.""")
public record ParkingView(

        @Schema(description = """
                AVAILABLE=반경 안에 실시간 잔여면을 아는 주차장이 있음,
                STATIC_ONLY=주차장은 있으나 지금 자리가 있는지는 모름,
                NONE=두 공급자를 확인했고 반경 안에 주차장이 없음,
                NO_DATA=좌표가 없거나 공급자를 확인하지 못해 말할 수 없음""")
        ParkingStatus status,

        @Schema(description = """
                실시간 값의 신선도. AVAILABLE=방금 받음, STALE=최종 정상 데이터,
                NO_DATA=실시간을 받지 못함. status 와 다른 축입니다""")
        DataStatus dataStatus,

        @Schema(description = """
                주변 주차장 목록. 실시간을 아는 곳을 먼저 담고 각 묶음 안에서 가까운 순입니다.
                거리순이 아닌 이유는 실시간이 이 신호의 목적이기 때문입니다""")
        List<ParkingLotView> lots,

        @Schema(description = "실시간 값을 받은 시각. 실시간이 없으면 null")
        LocalDateTime observedAt,

        @Schema(description = "데이터 출처. 두 공급자가 섞이면 함께 적습니다",
                example = "강릉시 교통정보 조회서비스 · 전국주차장정보표준데이터")
        String source
) {

    /** 좌표가 없거나 두 공급자를 모두 확인하지 못한 상태. */
    public static ParkingView noData() {
        return new ParkingView(ParkingStatus.NO_DATA, DataStatus.NO_DATA, List.of(), null, null);
    }

    /** 확인했고 반경 안에 주차장이 없는 상태. 정보 없음과 구분한다. */
    public static ParkingView none(DataStatus dataStatus, String source) {
        return new ParkingView(ParkingStatus.NONE, dataStatus, List.of(), null, source);
    }
}
