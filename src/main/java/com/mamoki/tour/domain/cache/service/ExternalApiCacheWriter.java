package com.mamoki.tour.domain.cache.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.cache.entity.ExternalApiCache;
import com.mamoki.tour.domain.cache.repository.ExternalApiCacheRepository;
import com.mamoki.tour.global.enums.ApiProvider;

/**
 * 캐시 쓰기만 맡는다. 저장·갱신이 일어나는 자리는 여기 하나다(#85).
 *
 * <p><b>왜 별도 빈인가.</b> {@link ExternalApiCacheService} 안의 메서드로 두면 같은 클래스
 * 내부 호출이라 프록시를 타지 않는다. {@code @Transactional} 이 붙어 있어도 전파 속성이
 * 아무 일도 하지 않고, 그 사실이 코드만 봐서는 드러나지 않는다.
 *
 * <p>두 문의 차이는 전파 속성 하나뿐이다. 어느 문으로 들어갈지는 호출자 트랜잭션이
 * 쓰기를 받아 주는지를 보고 {@link ExternalApiCacheService} 가 고른다.
 *
 * <p>두 문 모두 행을 <b>여기서 다시 읽는다</b>. 새 트랜잭션은 영속성 컨텍스트도 새로
 * 받으므로 바깥에서 읽어 둔 엔티티를 넘겨받아 봐야 더티 체킹 대상이 되지 않는다. 값만
 * 받고 조회는 안에서 하면 두 문이 같은 코드를 쓸 수 있다.
 */
@Component
public class ExternalApiCacheWriter {

    private final ExternalApiCacheRepository cacheRepository;

    public ExternalApiCacheWriter(ExternalApiCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    /** 호출자 트랜잭션에 합류해 적는다. 호출자가 롤백하면 캐시 쓰기도 함께 되돌아간다. */
    @Transactional
    public void write(ApiProvider provider, String requestKey, String responseBody,
                      LocalDateTime collectedAt, LocalDateTime expiresAt) {

        upsert(provider, requestKey, responseBody, collectedAt, expiresAt);
    }

    /**
     * 호출자 트랜잭션을 멈춰 두고 별도 트랜잭션에서 적는다. 호출자가 읽기 전용일 때 쓴다.
     *
     * <p>호출자가 나중에 롤백해도 이 쓰기는 남는다. 캐시는 "공급자가 그때 이렇게 답했다"는
     * 기록이라 호출자의 성패와 수명이 다르므로 남는 편이 맞다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInNewTransaction(ApiProvider provider, String requestKey, String responseBody,
                                      LocalDateTime collectedAt, LocalDateTime expiresAt) {

        upsert(provider, requestKey, responseBody, collectedAt, expiresAt);
    }

    private void upsert(ApiProvider provider, String requestKey, String responseBody,
                        LocalDateTime collectedAt, LocalDateTime expiresAt) {

        cacheRepository.findByProviderAndRequestKey(provider, requestKey).ifPresentOrElse(
                entry -> entry.refresh(responseBody, collectedAt, expiresAt),
                () -> cacheRepository.save(ExternalApiCache.builder()
                        .provider(provider)
                        .requestKey(requestKey)
                        .responseBody(responseBody)
                        .collectedAt(collectedAt)
                        .expiresAt(expiresAt)
                        .build()));
    }
}
