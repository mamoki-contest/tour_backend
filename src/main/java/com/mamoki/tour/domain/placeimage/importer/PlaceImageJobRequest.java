package com.mamoki.tour.domain.placeimage.importer;

/**
 * 대표 사진 보강 배치 한 번의 실행 조건.
 *
 * @param refresh  이미 물어본 관광지도 다시 수집할지. 기본은 아니다 — 다시 물으면 같은
 *                 답을 받으면서 하루 한도만 깎는다. 검색어 규칙이나 필터를 바꿨을 때만 켠다.
 * @param lawdCode 이 시·군만 본다. null 이면 강원 전체. 실측이나 부분 재수집에 쓴다.
 */
public record PlaceImageJobRequest(boolean refresh, String lawdCode) {

    public static PlaceImageJobRequest of(boolean refresh, String lawdCode) {
        return new PlaceImageJobRequest(refresh,
                lawdCode == null || lawdCode.isBlank() ? null : lawdCode.strip());
    }

    public boolean hasDistrict() {
        return lawdCode != null;
    }
}
