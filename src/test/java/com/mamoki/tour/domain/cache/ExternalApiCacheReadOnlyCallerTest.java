package com.mamoki.tour.domain.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.entity.ExternalApiCache;
import com.mamoki.tour.domain.cache.repository.ExternalApiCacheRepository;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.enums.DataStatus;

/**
 * 읽기 전용 트랜잭션 안에서 부른 캐시가 그래도 적히는지 본다(#85).
 *
 * <p>조회 서비스에 {@code @Transactional(readOnly = true)} 를 붙이는 것은 흔한 일이고,
 * 그 안에서 캐시를 부르면 캐시 쓰기가 호출자 트랜잭션에 합류한다. 읽기 전용 트랜잭션은
 * 쓰기를 받아 주지 않으므로 <b>그 키의 첫 호출에서만</b> 깨진다. 캐시가 이미 채워진
 * 환경에서는 드러나지 않아 PR #68 에서는 호출자 쪽의 {@code readOnly} 를 떼는 것으로
 * 넘어갔다. 여기서는 원인 쪽을 고정한다.
 *
 * <p>테스트 자체에는 {@code @Transactional} 을 붙이지 않는다. 캐시 쓰기가 호출자와 다른
 * 트랜잭션에서 커밋되는지를 보려면 커밋된 결과를 <b>바깥에서</b> 읽어야 하기 때문이다.
 * 호출자 트랜잭션은 {@link TransactionTemplate} 으로 직접 만든다 — 프록시를 거치지 않아
 * 읽기 전용 경계만 정확히 재현할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExternalApiCacheReadOnlyCallerTest {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String MISS_KEY = "readOnlyCaller-miss";
    private static final String EXPIRED_KEY = "readOnlyCaller-expired";

    @Autowired
    private ExternalApiCacheService cacheService;

    @Autowired
    private ExternalApiCacheRepository cacheRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void clearOwnKeys() {
        cacheRepository.findByProviderAndRequestKey(ApiProvider.KOR_SERVICE2, MISS_KEY)
                .ifPresent(cacheRepository::delete);
        cacheRepository.findByProviderAndRequestKey(ApiProvider.KOR_SERVICE2, EXPIRED_KEY)
                .ifPresent(cacheRepository::delete);
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션 안에서 부른 첫 fetch 도 캐시를 적는다")
    void writesCacheEvenInsideReadOnlyCaller() {
        CachedResponse response = inReadOnlyTransaction(() -> cacheService.fetch(
                ApiProvider.KOR_SERVICE2, MISS_KEY, () -> "첫 응답", TTL));

        assertThat(response.status()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(response.body()).isEqualTo("첫 응답");

        // 호출자 트랜잭션이 끝난 뒤 바깥에서 읽는다. 행이 남아 있어야 한다.
        assertThat(cacheRepository.findByProviderAndRequestKey(ApiProvider.KOR_SERVICE2, MISS_KEY))
                .get()
                .extracting(ExternalApiCache::getResponseBody)
                .isEqualTo("첫 응답");
    }

    @Test
    @DisplayName("읽기 전용 트랜잭션 안에서 만료된 캐시를 갱신하면 갱신분이 남는다")
    void refreshesExpiredCacheEvenInsideReadOnlyCaller() {
        LocalDateTime now = LocalDateTime.now();
        cacheRepository.saveAndFlush(ExternalApiCache.builder()
                .provider(ApiProvider.KOR_SERVICE2)
                .requestKey(EXPIRED_KEY)
                .responseBody("낡은 응답")
                .collectedAt(now.minusDays(2))
                .expiresAt(now.minusDays(1))
                .build());

        CachedResponse response = inReadOnlyTransaction(() -> cacheService.fetch(
                ApiProvider.KOR_SERVICE2, EXPIRED_KEY, () -> "새 응답", TTL));

        assertThat(response.status()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(response.body()).isEqualTo("새 응답");

        // 갱신은 더티 체킹으로 이뤄진다. 읽기 전용 트랜잭션은 플러시를 건너뛰므로
        // 예외 하나 없이 낡은 응답이 그대로 남는 것이 이 버그의 가장 조용한 모습이다.
        assertThat(cacheRepository.findByProviderAndRequestKey(ApiProvider.KOR_SERVICE2, EXPIRED_KEY))
                .get()
                .extracting(ExternalApiCache::getResponseBody)
                .isEqualTo("새 응답");
        assertThat(cacheRepository.findByProviderAndRequestKey(ApiProvider.KOR_SERVICE2, EXPIRED_KEY))
                .get()
                .extracting(entry -> entry.isExpired(LocalDateTime.now()))
                .isEqualTo(false);
    }

    private CachedResponse inReadOnlyTransaction(java.util.function.Supplier<CachedResponse> work) {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        return readOnly.execute(status -> work.get());
    }
}
