package com.mamoki.tour.domain.visittiming.dto;

import java.util.List;

/**
 * 관광지 상세에 담는 날짜 탐색 결과.
 *
 * <p>목록은 항목마다 판정 하나만 필요하지만, 상세는 30일을 펼쳐 보여 준다. 둘은 같은 분포와
 * 같은 경계에서 나오므로 목록의 판정과 상세의 그 날 판정이 어긋나지 않는다.
 *
 * @param summary 유연 모드 판정. 이 장소의 한산 예상일과 지원 범위를 담는다.
 * @param daily   지원 범위 30일의 하루치 판정. 날짜 오름차순이다.
 */
public record VisitTimingDetail(VisitTiming summary, List<DailyVisitTiming> daily) {
}
