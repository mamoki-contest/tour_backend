package com.mamoki.tour.domain.region.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.region.dto.RegionVisitScale;
import com.mamoki.tour.domain.region.dto.RegionVisitScaleResponse;
import com.mamoki.tour.domain.region.dto.RegionVisitors;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.enums.RegionVisitLevel;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.region.support.RegionVisitLevels;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.datalab.DataLabClient;
import com.mamoki.tour.infra.datalab.DataLabItemConverter;
import com.mamoki.tour.infra.datalab.DataLabProperties;
import com.mamoki.tour.infra.datalab.dto.DataLabItem;

/**
 * 강원 시·군의 방문 규모를 같은 기준 기간으로 모아 상대 구간을 매긴다.
 *
 * <p>공급자가 지역 필터를 받지 않아 전국 시·군구가 통째로 온다. 강원만 남기는 일은 여기서
 * 하며, 기준은 region_code 에 시드된 18개 시·군이다.
 *
 * <p>읍·면·동 단위는 다루지 않는다. 공급자가 시·군구까지만 제공하고, 더 잘게 나눈 값을
 * 우리가 추정해 만들면 근거 없는 숫자가 지도에 올라간다.
 */
@Service
public class RegionVisitScaleService {

    /** 한국관광공사 영역 코드. 강원. */
    private static final String GANGWON_AREA_CODE = "32";

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(RegionVisitScaleService.class);

    private final DataLabClient dataLabClient;
    private final DataLabProperties properties;
    private final ExternalApiCacheService cacheService;
    private final RegionCodeRepository regionCodeRepository;

    public RegionVisitScaleService(DataLabClient dataLabClient,
                                   DataLabProperties properties,
                                   ExternalApiCacheService cacheService,
                                   RegionCodeRepository regionCodeRepository) {
        this.dataLabClient = dataLabClient;
        this.properties = properties;
        this.cacheService = cacheService;
        this.regionCodeRepository = regionCodeRepository;
    }

    public RegionVisitScaleResponse getVisitScale() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = properties.resolveStartDate(today);
        LocalDate endDate = properties.resolveEndDate(today);

        List<RegionCode> regions = regionCodeRepository.findAllByAreaCode(GANGWON_AREA_CODE).stream()
                .sorted(Comparator.comparing(RegionCode::getLawdCode))
                .toList();

        Fetched fetched = fetchWindow(startDate, endDate);

        Map<String, RegionVisitors> visitorsByLawdCode = new LinkedHashMap<>();
        for (RegionVisitors visitors : DataLabItemConverter.convertAll(fetched.items())) {
            visitorsByLawdCode.put(visitors.lawdCode(), visitors);
        }

        // 강원 밖 시·군구는 상대 구간 계산에서도 뺀다. 전국과 견주면 강원 안의 차이가 뭉개진다.
        List<RegionVisitors> gangwon = regions.stream()
                .map(region -> visitorsByLawdCode.get(region.getLawdCode()))
                .filter(Objects::nonNull)
                .toList();

        Map<String, RegionVisitLevel> levels = RegionVisitLevels.assign(gangwon);
        Map<String, Integer> ranks = RegionVisitLevels.rank(gangwon);

        List<RegionVisitScale> items = new ArrayList<>(regions.size());
        for (RegionCode region : regions) {
            RegionVisitors visitors = visitorsByLawdCode.get(region.getLawdCode());

            if (visitors == null) {
                items.add(RegionVisitScale.noData(
                        region.getLawdCode(), region.getSigunguCode(), region.getName()));
                continue;
            }

            items.add(new RegionVisitScale(
                    region.getLawdCode(),
                    region.getSigunguCode(),
                    region.getName(),
                    visitors.visitorCount(),
                    levels.get(region.getLawdCode()),
                    ranks.get(region.getLawdCode()),
                    visitors.dayCount(),
                    DataStatus.AVAILABLE));
        }

        // 한 시·군도 얻지 못했으면 기간만 남기고 정보 없음으로 알린다.
        DataStatus status = gangwon.isEmpty() ? DataStatus.NO_DATA : fetched.status();
        LocalDateTime collectedAt = status == DataStatus.NO_DATA ? null : fetched.collectedAt();

        return new RegionVisitScaleResponse(items, startDate, endDate, regions.size(),
                gangwon.size(), status, collectedAt, DataLabItemConverter.SOURCE);
    }

    /**
     * 기준 기간의 전국 응답을 페이지 단위로 모은다.
     *
     * <p>한 페이지라도 최종 정상 데이터로 응답했다면 전체를 그 상태로 알린다. 일부만 신선한
     * 결과를 유효한 데이터로 보이게 하지 않는다.
     */
    private Fetched fetchWindow(LocalDate startDate, LocalDate endDate) {
        List<DataLabItem> all = new ArrayList<>();
        DataStatus status = null;
        LocalDateTime collectedAt = null;
        int totalCount = Integer.MAX_VALUE;

        for (int pageNo = 1; pageNo <= properties.maxPages(); pageNo++) {
            String requestKey = dataLabClient.regionVisitorsKey(startDate, endDate, pageNo);
            final int page = pageNo;

            CachedResponse cached = cacheService.fetch(
                    ApiProvider.REGION_VISITOR,
                    requestKey,
                    () -> dataLabClient.regionVisitorsJson(startDate, endDate, page),
                    CACHE_TTL);

            if (!cached.hasBody()) {
                break;
            }

            List<DataLabItem> items;
            try {
                var parsed = dataLabClient.parse(cached.body());
                items = parsed.items();
                totalCount = parsed.totalCount();
            } catch (ExternalApiException e) {
                log.warn("캐시된 DataLab 응답을 해석하지 못했습니다. requestKey={}", requestKey, e);
                break;
            }

            if (items.isEmpty()) {
                break;
            }

            all.addAll(items);

            if (collectedAt == null) {
                collectedAt = cached.collectedAt();
            }

            if (status == null || cached.status() == DataStatus.STALE) {
                status = cached.status();
            }

            if (all.size() >= totalCount) {
                break;
            }
        }

        if (all.size() < totalCount && totalCount != Integer.MAX_VALUE) {
            log.warn("DataLab 응답을 끝까지 받지 못했습니다. 받은 행={}, 전체={}, 상한 페이지={}",
                    all.size(), totalCount, properties.maxPages());
        }

        return new Fetched(all, status == null ? DataStatus.NO_DATA : status, collectedAt);
    }

    private record Fetched(List<DataLabItem> items, DataStatus status, LocalDateTime collectedAt) {
    }
}
