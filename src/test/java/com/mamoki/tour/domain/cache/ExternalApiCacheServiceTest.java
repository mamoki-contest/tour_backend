package com.mamoki.tour.domain.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.entity.ExternalApiCache;
import com.mamoki.tour.domain.cache.repository.ExternalApiCacheRepository;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.exception.ExternalApiException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExternalApiCacheServiceTest {

    private static final Duration TTL = Duration.ofHours(24);

    @Autowired
    private ExternalApiCacheService cacheService;

    @Autowired
    private ExternalApiCacheRepository cacheRepository;

    @Test
    @DisplayName("캐시가 없으면 공급자를 호출하고 결과를 저장한다")
    void fetchesAndStoresOnMiss() {
        CachedResponse response = cacheService.fetch(
                ApiProvider.KOR_SERVICE2, "miss", () -> "첫 응답", TTL);

        assertThat(response.status()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(response.body()).isEqualTo("첫 응답");
        assertThat(cacheRepository.findByProviderAndRequestKey(ApiProvider.KOR_SERVICE2, "miss"))
                .isPresent();
    }

    @Test
    @DisplayName("유효한 캐시가 있으면 공급자를 호출하지 않는다")
    void reusesFreshCache() {
        AtomicInteger calls = new AtomicInteger();

        cacheService.fetch(ApiProvider.KOR_SERVICE2, "hit",
                () -> { calls.incrementAndGet(); return "응답"; }, TTL);
        CachedResponse second = cacheService.fetch(ApiProvider.KOR_SERVICE2, "hit",
                () -> { calls.incrementAndGet(); return "다시 호출됨"; }, TTL);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(second.body()).isEqualTo("응답");
        assertThat(second.status()).isEqualTo(DataStatus.AVAILABLE);
    }

    @Test
    @DisplayName("만료된 캐시는 갱신한다")
    void refreshesExpiredCache() {
        LocalDateTime now = LocalDateTime.now();
        cacheRepository.save(ExternalApiCache.builder()
                .provider(ApiProvider.KOR_SERVICE2)
                .requestKey("expired")
                .responseBody("낡은 응답")
                .collectedAt(now.minusDays(2))
                .expiresAt(now.minusDays(1))
                .build());

        CachedResponse response = cacheService.fetch(
                ApiProvider.KOR_SERVICE2, "expired", () -> "새 응답", TTL);

        assertThat(response.status()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(response.body()).isEqualTo("새 응답");
        assertThat(cacheRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("갱신에 실패하면 최종 정상 데이터를 기준 시점과 함께 돌려준다")
    void fallsBackToLastGoodData() {
        LocalDateTime collectedAt = LocalDateTime.now().minusDays(2);
        cacheRepository.save(ExternalApiCache.builder()
                .provider(ApiProvider.KOR_SERVICE2)
                .requestKey("stale")
                .responseBody("최종 정상 응답")
                .collectedAt(collectedAt)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build());

        CachedResponse response = cacheService.fetch(ApiProvider.KOR_SERVICE2, "stale",
                () -> { throw new ExternalApiException(ApiProvider.KOR_SERVICE2, "공급자 장애"); },
                TTL);

        assertThat(response.status()).isEqualTo(DataStatus.STALE);
        assertThat(response.body()).isEqualTo("최종 정상 응답");
        assertThat(response.collectedAt()).isEqualTo(collectedAt);
    }

    @Test
    @DisplayName("최종 정상 데이터도 없으면 정보 없음을 반환하고 예외를 전파하지 않는다")
    void returnsNoDataWhenNothingCached() {
        CachedResponse response = cacheService.fetch(ApiProvider.KOR_SERVICE2, "nothing",
                () -> { throw new ExternalApiException(ApiProvider.KOR_SERVICE2, "공급자 장애"); },
                TTL);

        assertThat(response.status()).isEqualTo(DataStatus.NO_DATA);
        assertThat(response.hasBody()).isFalse();
        assertThat(response.collectedAt()).isNull();
    }
}
