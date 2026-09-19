package com.mamoki.tour.infra.gnits.dto;

/**
 * 강릉시 교통정보 조회서비스의 공통 응답 봉투.
 *
 * <p>{@code header} / {@code body} 가 최상위에 바로 오고, 항목은 {@code body.items.item}
 * 아래에 한 겹 더 싸여 있다. 성공 판정은 HTTP 200 과 {@code header.resultCode == "00"} 을
 * 둘 다 본다.
 */
public interface GnItsEnvelope {

    GnItsHeader header();

    int totalCount();
}
