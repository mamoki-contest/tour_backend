package com.mamoki.tour.domain.currentaccess.dto;

import com.mamoki.tour.global.enums.DataStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관광지 주변 주차 여건.
 *
 * <p>아직 상태만 있다. 한국교통안전공단 주차정보 API 가 심의승인 대기 중이라 실제 응답을
 * 한 번도 보지 못했다. 포털 문서에도 총 주차면은 있으나 잔여면과 기준시각이 보이지 않는다.
 *
 * <p>추측으로 필드를 만들지 않는다. 승인 후 실제 응답을 확인하고 이 안쪽을 채운다. 바깥
 * 계약({@code currentAccess.parking})은 그대로 두므로 그때 프론트가 다시 맞출 필요가 없다.
 */
@Schema(description = """
        주차 여건. 공급자 승인 대기 중이라 현재는 항상 정보 없음입니다.
        정보 없음을 '주차 자리 없음' 으로 표시하지 마세요.""")
public record ParkingView(

        @Schema(description = "NO_DATA=정보 없음. 승인 후 AVAILABLE 이 추가됩니다")
        DataStatus status
) {

    public static ParkingView noData() {
        return new ParkingView(DataStatus.NO_DATA);
    }
}
