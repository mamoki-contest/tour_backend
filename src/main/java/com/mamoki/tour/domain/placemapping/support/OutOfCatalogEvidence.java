package com.mamoki.tour.domain.placemapping.support;

/**
 * 카탈로그 대상이 아니라고 본 근거.
 *
 * <p>판정만 남기고 근거를 버리면, 나중에 분모에서 빠진 행을 보고도 왜 빠졌는지 되짚을 수
 * 없다. 규칙을 고쳤을 때 어느 행이 달라지는지도 알 수 없다. 그래서 맞은 규칙을 그대로 적는다.
 *
 * @param categoryRule   맞은 카카오 카테고리 규칙. 사전 파일에 적힌 줄 그대로다.
 * @param nameSuffixRule 맞은 이름 접미어 규칙.
 */
public record OutOfCatalogEvidence(String categoryRule, String nameSuffixRule) {
}
