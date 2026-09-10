package com.mamoki.tour.domain.region.dto;

/**
 * 한 시·군의 기준 기간 방문 규모 집계.
 *
 * @param lawdCode     법정동 시·군구 코드 5자리
 * @param name         공급자가 내려준 시·군명. 표시에는 region_code 의 이름을 쓴다.
 * @param visitorCount 기준 기간의 외지인·외국인 합계. 현지인은 담지 않는다.
 * @param dayCount     실제로 값이 있던 날 수. 기준 기간보다 적으면 부분 집계다.
 */
public record RegionVisitors(String lawdCode, String name, long visitorCount, int dayCount) {
}
