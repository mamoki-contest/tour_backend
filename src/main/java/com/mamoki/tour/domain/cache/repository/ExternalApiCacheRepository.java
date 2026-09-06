package com.mamoki.tour.domain.cache.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mamoki.tour.domain.cache.entity.ExternalApiCache;
import com.mamoki.tour.global.enums.ApiProvider;

public interface ExternalApiCacheRepository extends JpaRepository<ExternalApiCache, Long> {

    /**
     * 만료 여부와 무관하게 조회한다. 만료된 행도 최종 정상 데이터 폴백에 사용하므로
     * 호출 측에서 {@link ExternalApiCache#isExpired} 로 판단한다.
     */
    Optional<ExternalApiCache> findByProviderAndRequestKey(ApiProvider provider, String requestKey);
}
