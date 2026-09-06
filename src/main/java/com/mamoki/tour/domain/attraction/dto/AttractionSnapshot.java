package com.mamoki.tour.domain.attraction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 공급자 응답을 표준 계약으로 변환한 결과.
 *
 * <p>결측은 빈 문자열이나 0 이 아니라 null 로 표현한다. 카탈로그 적재와 목록 응답이
 * 같은 계약을 공유하도록 어댑터가 이 형태까지 책임진다.
 *
 * @param lawdCode 법정동 시·군 코드 5자리. 지역코드 매핑의 기준 키.
 * @param baseAt   공급자가 알려준 데이터 기준 시점. 우리 저장 시각과 구분한다.
 */
public record AttractionSnapshot(
        String contentId,
        String name,
        String imageUrl,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String contentTypeId,
        String lawdCode,
        LocalDateTime baseAt,
        String source
) {
}
