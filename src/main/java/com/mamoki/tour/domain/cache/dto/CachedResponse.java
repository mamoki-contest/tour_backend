package com.mamoki.tour.domain.cache.dto;

import java.time.LocalDateTime;

import com.mamoki.tour.global.enums.DataStatus;

/**
 * 캐시 계층이 돌려주는 결과.
 *
 * @param body        공급자 원본 응답. {@link DataStatus#NO_DATA} 일 때만 null.
 * @param collectedAt 이 본문을 수집한 시각. 응답의 기준 시점으로 사용한다.
 * @param status      AVAILABLE(유효) / STALE(최종 정상 데이터) / NO_DATA(정보 없음)
 */
public record CachedResponse(String body, LocalDateTime collectedAt, DataStatus status) {

    public static CachedResponse available(String body, LocalDateTime collectedAt) {
        return new CachedResponse(body, collectedAt, DataStatus.AVAILABLE);
    }

    public static CachedResponse stale(String body, LocalDateTime collectedAt) {
        return new CachedResponse(body, collectedAt, DataStatus.STALE);
    }

    public static CachedResponse noData() {
        return new CachedResponse(null, null, DataStatus.NO_DATA);
    }

    public boolean hasBody() {
        return body != null;
    }
}
