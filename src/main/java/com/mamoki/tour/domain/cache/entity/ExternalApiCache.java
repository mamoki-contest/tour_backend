package com.mamoki.tour.domain.cache.entity;

import java.time.LocalDateTime;

import com.mamoki.tour.global.entity.BaseEntity;
import com.mamoki.tour.global.enums.ApiProvider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 외부 API 응답 캐시.
 *
 * <p>만료된 행을 삭제하지 않는다. 갱신에 실패했을 때 최종 정상 데이터로 응답해야 하므로,
 * 만료 여부는 expiresAt 으로 판단하고 본문은 그대로 보관한다. 만료된 데이터로 응답할 때는
 * collectedAt 을 기준 시점으로 함께 노출한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "external_api_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_external_api_cache_provider_request",
                columnNames = {"provider", "request_key"}),
        indexes = @Index(name = "idx_external_api_cache_expires_at", columnList = "expires_at")
)
public class ExternalApiCache extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private ApiProvider provider;

    /** 같은 공급자 안에서 요청을 식별하는 키. 오퍼레이션명과 파라미터를 정규화해 만든다. */
    @Column(name = "request_key", nullable = false, length = 500)
    private String requestKey;

    @Lob
    @Column(name = "response_body", nullable = false, columnDefinition = "LONGTEXT")
    private String responseBody;

    /** 공급자로부터 응답을 받은 시각. 응답의 기준 시점으로 사용한다. */
    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    /** 캐시 만료 시각. 기본 24시간. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    private ExternalApiCache(ApiProvider provider, String requestKey, String responseBody,
                            LocalDateTime collectedAt, LocalDateTime expiresAt) {
        this.provider = provider;
        this.requestKey = requestKey;
        this.responseBody = responseBody;
        this.collectedAt = collectedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    /** 갱신 성공 시 기존 행을 덮어쓴다. 최종 정상 데이터를 잃지 않도록 삭제 후 재삽입하지 않는다. */
    public void refresh(String responseBody, LocalDateTime collectedAt, LocalDateTime expiresAt) {
        this.responseBody = responseBody;
        this.collectedAt = collectedAt;
        this.expiresAt = expiresAt;
    }
}
