package com.mamoki.tour.domain.cache.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.entity.ExternalApiCache;
import com.mamoki.tour.domain.cache.repository.ExternalApiCacheRepository;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;

/**
 * 외부 공급자 호출을 캐시와 폴백으로 감싼다.
 *
 * <p>공급자 장애는 여기서 흡수한다. {@link ExternalApiException} 이 컨트롤러까지 올라가지
 * 않게 하고, 대신 최종 정상 데이터({@code STALE})나 정보 없음({@code NO_DATA})으로 변환한다.
 * 이것이 "결측을 오류가 아닌 정상 응답의 한 상태로 다룬다"는 PRD 공통 원칙의 구현 지점이다.
 */
@Service
public class ExternalApiCacheService {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiCacheService.class);

    private final ExternalApiCacheRepository cacheRepository;

    public ExternalApiCacheService(ExternalApiCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    /**
     * 유효한 캐시가 있으면 그대로 쓰고, 없으면 공급자를 호출한다.
     *
     * @param loader 공급자 호출. 실패 시 {@link ExternalApiException} 을 던져야 한다.
     * @param ttl    캐시 유지 기간. 관광 데이터는 24시간을 사용한다.
     */
    @Transactional
    public CachedResponse fetch(ApiProvider provider, String requestKey,
                                Supplier<String> loader, Duration ttl) {

        LocalDateTime now = LocalDateTime.now();
        Optional<ExternalApiCache> cached = cacheRepository.findByProviderAndRequestKey(provider, requestKey);

        if (cached.isPresent() && !cached.get().isExpired(now)) {
            ExternalApiCache hit = cached.get();
            return CachedResponse.available(hit.getResponseBody(), hit.getCollectedAt());
        }

        try {
            String body = loader.get();
            LocalDateTime expiresAt = now.plus(ttl);

            cached.ifPresentOrElse(
                    entry -> entry.refresh(body, now, expiresAt),
                    () -> cacheRepository.save(ExternalApiCache.builder()
                            .provider(provider)
                            .requestKey(requestKey)
                            .responseBody(body)
                            .collectedAt(now)
                            .expiresAt(expiresAt)
                            .build()));

            return CachedResponse.available(body, now);

        } catch (ExternalApiException e) {
            // 만료된 데이터라도 있으면 기준 시점을 밝히고 그대로 응답한다.
            if (cached.isPresent()) {
                ExternalApiCache fallback = cached.get();
                log.warn("{} 갱신 실패, 최종 정상 데이터로 응답합니다. requestKey={}", provider, requestKey, e);
                return CachedResponse.stale(fallback.getResponseBody(), fallback.getCollectedAt());
            }

            // 최종 정상 데이터도 없으면 다른 신호로 추정하지 않고 정보 없음을 반환한다.
            log.warn("{} 호출 실패, 최종 정상 데이터가 없어 정보 없음으로 처리합니다. requestKey={}",
                    provider, requestKey, e);
            return CachedResponse.noData();
        }
    }
}
