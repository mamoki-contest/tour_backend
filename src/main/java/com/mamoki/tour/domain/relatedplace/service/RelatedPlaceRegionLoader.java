package com.mamoki.tour.domain.relatedplace.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.relatedplace.dto.RegionRelatedPlaces;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.tarrltetar.TarRlteTarClient;
import com.mamoki.tour.infra.tarrltetar.TarRlteTarItemConverter;
import com.mamoki.tour.infra.tarrltetar.dto.TarRlteTarItem;
import com.mamoki.tour.infra.tarrltetar.dto.TarRlteTarResponse;

/**
 * 시·군 하나의 연관 장소를 모은다.
 *
 * <p>한 시·군의 행 수는 (기준 관광지 수 × 연관 장소 수) 라 한 페이지에 담기지 않는다.
 * 첫 페이지의 totalCount 로 남은 페이지를 이어 받되, 페이지 수에 상한을 둔다. 상한에서
 * 잘린 관광지는 값을 지어내지 않고 연관 정보 없음으로 남는다.
 *
 * <p>상세 조회(#7)와 장소 매핑 배치(#55)가 같은 응답을 본다. 두 곳이 각자 페이지를 이어
 * 받으면 상한과 실패 처리가 조용히 갈라진다. 24시간 캐시도 한 자리에서만 열린다.
 */
@Service
public class RelatedPlaceRegionLoader {

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(RelatedPlaceRegionLoader.class);

    private final TarRlteTarClient tarRlteTarClient;
    private final ExternalApiCacheService cacheService;

    public RelatedPlaceRegionLoader(TarRlteTarClient tarRlteTarClient,
                                    ExternalApiCacheService cacheService) {
        this.tarRlteTarClient = tarRlteTarClient;
        this.cacheService = cacheService;
    }

    public String baseYm() {
        return tarRlteTarClient.baseYm();
    }

    public RegionRelatedPlaces load(String lawdCode) {
        List<TarRlteTarItem> items = new ArrayList<>();
        DataStatus status = null;
        LocalDateTime collectedAt = null;
        int totalPages = 1;

        for (int pageNo = 1; pageNo <= totalPages; pageNo++) {
            CachedResponse cached = fetchPage(lawdCode, pageNo);

            if (!cached.hasBody()) {
                // 첫 페이지부터 못 받으면 정보 없음, 뒤 페이지가 빠지면 일부만 모인 것이다.
                status = status == null ? DataStatus.NO_DATA : DataStatus.STALE;
                log.warn("TarRlteTar 응답을 받지 못했습니다. lawdCode={} pageNo={}", lawdCode, pageNo);
                break;
            }

            TarRlteTarResponse parsed;
            try {
                parsed = tarRlteTarClient.parse(cached.body());
            } catch (ExternalApiException e) {
                // 캐시에 남아 있던 본문이 더 이상 해석되지 않는 경우. 빈 목록으로 위장하지 않는다.
                log.warn("캐시된 TarRlteTar 응답을 해석하지 못했습니다. lawdCode={} pageNo={}",
                        lawdCode, pageNo, e);
                status = status == null ? DataStatus.NO_DATA : DataStatus.STALE;
                break;
            }

            items.addAll(parsed.items());
            status = worse(status, cached.status());
            collectedAt = earlier(collectedAt, cached.collectedAt());

            if (pageNo == 1) {
                totalPages = pageCount(parsed.totalCount());
            }
        }

        if (status == null || status == DataStatus.NO_DATA || items.isEmpty()) {
            return RegionRelatedPlaces.empty();
        }

        return RegionRelatedPlaces.of(TarRlteTarItemConverter.convertAll(items), status, collectedAt);
    }

    private CachedResponse fetchPage(String lawdCode, int pageNo) {
        return cacheService.fetch(
                ApiProvider.TAR_RLTE_TAR,
                tarRlteTarClient.relatedListKey(lawdCode, pageNo),
                () -> tarRlteTarClient.relatedListJson(lawdCode, pageNo),
                CACHE_TTL);
    }

    private int pageCount(int totalCount) {
        int rowsPerPage = tarRlteTarClient.rowsPerPage();
        int needed = (totalCount + rowsPerPage - 1) / rowsPerPage;

        return Math.max(1, Math.min(needed, tarRlteTarClient.maxPages()));
    }

    /** 여러 페이지를 합칠 때는 가장 나쁜 상태를 그 시·군의 상태로 삼는다. */
    private static DataStatus worse(DataStatus current, DataStatus next) {
        if (current == null) {
            return next;
        }

        return current.ordinal() >= next.ordinal() ? current : next;
    }

    /** 기준 시점은 가장 오래된 페이지에 맞춘다. 실제보다 최신이라고 말하지 않기 위해서다. */
    private static LocalDateTime earlier(LocalDateTime current, LocalDateTime next) {
        if (current == null) {
            return next;
        }

        return next == null || current.isBefore(next) ? current : next;
    }
}
