package com.mamoki.tour.domain.cache.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
 *
 * <p>캐시 쓰기는 이 클래스에 없다. 전부 {@link ExternalApiCacheWriter} 를 거친다 — 쓰기가
 * 호출자 트랜잭션에 합류할 수 있는지에 따라 문이 갈리기 때문이다(#85).
 */
@Service
public class ExternalApiCacheService {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiCacheService.class);

    private final ExternalApiCacheRepository cacheRepository;
    private final ExternalApiCacheWriter cacheWriter;

    public ExternalApiCacheService(ExternalApiCacheRepository cacheRepository,
                                   ExternalApiCacheWriter cacheWriter) {
        this.cacheRepository = cacheRepository;
        this.cacheWriter = cacheWriter;
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

            writeCache(provider, requestKey, body, now, expiresAt);

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

    /**
     * 캐시를 적는다. 호출자 트랜잭션이 쓰기를 받아 주면 거기에 합류하고, 읽기 전용이면
     * 별도 트랜잭션으로 빠져나간다.
     *
     * <p><b>왜 늘 별도 트랜잭션이 아닌가.</b> 별도 트랜잭션은 호출자가 그 행을 다시 읽지
     * 못하게 만든다. 호출자의 스냅샷은 이미 잡혀 있어 나중에 커밋된 행이 보이지 않으므로,
     * 같은 트랜잭션에서 같은 키를 두 번 부르면 공급자를 두 번 부르게 된다. 호출자가 그
     * 행을 먼저 건드린 뒤라면 그 행의 잠금을 자기 자신이 기다려 잠금 대기 시간 초과로
     * 끝난다. 합류할 수 있을 때 합류하는 것이 지금까지의 동작이고, 그대로 둔다.
     *
     * <p>읽기 전용일 때만 문이 갈린다. 그 자리에서 합류하면 삽입은 예외로 터지고
     * 갱신(더티 체킹)은 플러시를 건너뛰어 <b>아무 소리 없이</b> 사라진다. 캐시를 못 적는
     * 것이 조회를 실패시킬 이유는 아니므로 빠져나가서 적는다.
     */
    private void writeCache(ApiProvider provider, String requestKey, String body,
                            LocalDateTime collectedAt, LocalDateTime expiresAt) {

        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            cacheWriter.writeInNewTransaction(provider, requestKey, body, collectedAt, expiresAt);
            return;
        }

        cacheWriter.write(provider, requestKey, body, collectedAt, expiresAt);
    }
}
