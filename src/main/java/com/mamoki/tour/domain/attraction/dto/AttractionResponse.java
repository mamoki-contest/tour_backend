package com.mamoki.tour.domain.attraction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관광지 목록 항목의 표준 계약.
 *
 * <p>결측은 null 로 내려간다. 프론트는 null 을 `정보 없음`으로 표시하고 0 으로 해석하지 않는다.
 *
 * @param regionName 지역코드 매핑에서 찾은 시·군 이름. 매핑이 없으면 null.
 * @param centerRank 시·군 내부 중심관광지 순위. LocgoHubTarService1 연동 전까지 null.
 * @param baseAt     이 항목의 공급자 기준 시점.
 */
public record AttractionResponse(
        String contentId,
        String name,
        String imageUrl,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String contentTypeId,
        String lawdCode,
        String regionName,
        Integer centerRank,
        LocalDateTime baseAt
) {

    public static AttractionResponse of(AttractionSnapshot snapshot, String regionName) {
        return new AttractionResponse(
                snapshot.contentId(),
                snapshot.name(),
                snapshot.imageUrl(),
                snapshot.address(),
                snapshot.latitude(),
                snapshot.longitude(),
                snapshot.contentTypeId(),
                snapshot.lawdCode(),
                regionName,
                null,
                snapshot.baseAt()
        );
    }
}
