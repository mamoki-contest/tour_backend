package com.mamoki.tour.domain.visitorstats.importer;

/**
 * 공표월에 값이 있던 관광지 한 곳.
 *
 * <p>값이 없던 관광지는 아예 만들지 않는다. 0 으로 채우면 미집계와 실제 0 명이 섞인다.
 *
 * @param visitorCount `합계` 행의 값. 내국인과 외국인을 우리가 다시 더하지 않는다.
 */
public record VisitorStatsRow(String regionName, String placeName, long visitorCount) {
}
