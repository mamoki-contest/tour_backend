package com.mamoki.tour.domain.attraction.dto;

/**
 * 상세 조회에서만 얻을 수 있는 값을 목록 계약에 덧붙인 결과.
 *
 * <p>목록과 상세가 같은 {@link AttractionSnapshot} 을 공유하도록 상속이 아니라 조합으로 둔다.
 * 상세 전용 필드가 늘어나도 목록 계약이 흔들리지 않는다.
 *
 * <p>결측은 빈 문자열이 아니라 null 이다. 공급자는 값이 없을 때 빈 문자열을 주는데, 그대로
 * 두면 `값이 있다`고 오해하게 된다.
 *
 * @param homepage 공급자가 앵커 태그를 통째로 넣어 주기도 한다. 원본을 그대로 전달한다.
 */
public record AttractionDetailSnapshot(
        AttractionSnapshot basic,
        String zipcode,
        String tel,
        String homepage,
        String overview
) {
}
