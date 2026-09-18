package com.mamoki.tour.infra.its.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 국가교통정보센터 교통소통정보의 링크 한 구간.
 *
 * <p>좌표가 없다. 요청한 사각형 안의 구간이라는 것만 알 수 있고, 관광지에서 얼마나 떨어져
 * 있는지는 알 수 없다. 그래서 가장 가까운 도로를 고르지 못하고 사각형을 좁게 잡아 요약한다.
 *
 * @param speed       현재 통행 속도(km/h). 문자열로 온다.
 * @param travelTime  구간 통행시간(초). 문자열로 온다.
 * @param createdDate 관측 생성시각 yyyyMMddHHmmss. 5분 단위로 갱신된다.
 * @param roadName    도로명. 이름 없는 구간은 빈 문자열로 온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItsItem(
        String roadName,
        String roadDrcType,
        String linkNo,
        String linkId,
        String startNodeId,
        String endNodeId,
        String speed,
        String travelTime,
        String createdDate
) {
}
