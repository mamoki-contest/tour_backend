package com.mamoki.tour.domain.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.cache.entity.ExternalApiCache;
import com.mamoki.tour.domain.cache.repository.ExternalApiCacheRepository;
import com.mamoki.tour.global.enums.ApiProvider;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExternalApiCacheRepositoryTest {

    @Autowired
    private ExternalApiCacheRepository cacheRepository;

    @Test
    @DisplayName("만료된 캐시도 조회된다 - 최종 정상 데이터 폴백에 사용한다")
    void expiredCacheIsStillReadable() {
        LocalDateTime now = LocalDateTime.now();

        cacheRepository.save(ExternalApiCache.builder()
                .provider(ApiProvider.KOR_SERVICE2)
                .requestKey("areaBasedList2?areaCode=32")
                .responseBody("{\"items\":[]}")
                .collectedAt(now.minusDays(2))
                .expiresAt(now.minusDays(1))
                .build());

        ExternalApiCache found = cacheRepository
                .findByProviderAndRequestKey(ApiProvider.KOR_SERVICE2, "areaBasedList2?areaCode=32")
                .orElseThrow();

        assertThat(found.isExpired(now)).isTrue();
        assertThat(found.getResponseBody()).isEqualTo("{\"items\":[]}");
        assertThat(found.getCollectedAt()).isEqualTo(now.minusDays(2));
    }

    @Test
    @DisplayName("유효 기간 안의 캐시는 만료되지 않은 것으로 판단한다")
    void freshCacheIsNotExpired() {
        LocalDateTime now = LocalDateTime.now();

        ExternalApiCache saved = cacheRepository.save(ExternalApiCache.builder()
                .provider(ApiProvider.TATS_CNCTR_RATE)
                .requestKey("forecast?contentId=126508")
                .responseBody("{}")
                .collectedAt(now)
                .expiresAt(now.plusHours(24))
                .build());

        assertThat(saved.isExpired(now)).isFalse();
    }

    @Test
    @DisplayName("갱신은 기존 행을 덮어쓰고 기준 시점을 옮긴다")
    void refreshOverwritesInPlace() {
        LocalDateTime now = LocalDateTime.now();

        ExternalApiCache cache = cacheRepository.save(ExternalApiCache.builder()
                .provider(ApiProvider.LOCGO_HUB_TAR)
                .requestKey("centerRank?areaCode=32")
                .responseBody("old")
                .collectedAt(now.minusDays(2))
                .expiresAt(now.minusDays(1))
                .build());

        cache.refresh("new", now, now.plusHours(24));

        ExternalApiCache found = cacheRepository
                .findByProviderAndRequestKey(ApiProvider.LOCGO_HUB_TAR, "centerRank?areaCode=32")
                .orElseThrow();

        assertThat(found.getResponseBody()).isEqualTo("new");
        assertThat(found.isExpired(now)).isFalse();
        assertThat(cacheRepository.count()).isEqualTo(1);
    }
}
