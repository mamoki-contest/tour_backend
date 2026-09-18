package com.mamoki.tour.domain.visitorstats.importer;

import java.util.List;

/**
 * 파일 하나를 읽어낸 결과.
 *
 * @param publishedMonth 값이 실제로 들어 있는 마지막 월(yyyyMM)
 * @param sourcePeriod   파일이 담고 있는 전체 기간(예: 202501-202606). 공표월과 다르다.
 * @param rows           공표월에 값이 있던 관광지들
 */
public record VisitorStatsWorkbook(String publishedMonth, String sourcePeriod,
                                   List<VisitorStatsRow> rows) {
}
