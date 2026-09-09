package com.mamoki.tour.domain.visittiming.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.attraction.support.Coordinates;
import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.visittiming.dto.AttractionForecast;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;
import com.mamoki.tour.domain.visittiming.dto.VisitTimingDetail;
import com.mamoki.tour.domain.visittiming.dto.VisitTimingVerdict;
import com.mamoki.tour.domain.visittiming.enums.DateMode;
import com.mamoki.tour.domain.visittiming.support.VisitTimingResolver;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.tatscnctrrate.TatsCnctrRateClient;
import com.mamoki.tour.infra.tatscnctrrate.TatsCnctrRateItemConverter;
import com.mamoki.tour.infra.tatscnctrrate.dto.TatsCnctrRateItem;
import com.mamoki.tour.infra.tatscnctrrate.dto.TatsCnctrRateResponse;

/**
 * 관광지 목록에 날짜 탐색 결과를 이어 붙인다.
 *
 * <p>공급자 조회 단위가 시·군이라, 목록에 실제로 등장한 시·군만큼만 호출한다. 강원은 시·군이
 * 18개뿐이고 응답을 24시간 캐시하므로, 한 페이지에 여러 시·군이 섞여도 하루 호출 수는
 * 시·군 수를 넘지 않는다. 관광지마다 1회씩 부르는 방식은 쓰지 않는다.
 *
 * <p>공급자 장애는 캐시 계층이 흡수하므로 여기서 예외가 새어 나가지 않는다. 매칭에 실패하거나
 * 예측이 없으면 값을 만들어내지 않고 {@code NO_DATA} 로 남긴다.
 *
 * <p>알려진 비용: 캐시가 비어 있는 상태에서 강원 전체를 훑으면 한 요청 안에서 시·군 수만큼
 * 순차 호출이 일어난다. 시·군 수를 줄여 막으면 나머지 관광지가 조용히 정보 없음이 되므로,
 * 값을 지우는 대신 읽기 제한 시간을 짧게 두고 24시간 캐시로 감당한다. 예열 배치는 별도 작업이다.
 */
@Service
public class VisitTimingService {

    /** 같은 이름이라도 이보다 멀면 다른 장소로 본다. CenterRankService 와 같은 기준이다. */
    private static final double MAX_MATCH_DISTANCE_METERS = 3_000;

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(VisitTimingService.class);

    private final TatsCnctrRateClient tatsCnctrRateClient;
    private final ExternalApiCacheService cacheService;

    public VisitTimingService(TatsCnctrRateClient tatsCnctrRateClient,
                              ExternalApiCacheService cacheService) {
        this.tatsCnctrRateClient = tatsCnctrRateClient;
        this.cacheService = cacheService;
    }

    public Map<String, VisitTiming> resolve(List<AttractionSnapshot> attractions,
                                            DateMode dateMode, LocalDate visitDate) {

        return resolve(attractions, dateMode, visitDate, LocalDate.now());
    }

    /**
     * @param dateMode  FIXED 면 visitDate 를 그 장소 기준으로 해석하고, FLEXIBLE 이면 한산 예상일을 고른다.
     * @param visitDate FIXED 의 선택일. FLEXIBLE 이면 null.
     * @param today     지원 범위(오늘부터 30일)의 기준일
     * @return 표준 관광지 식별자 → 날짜 탐색 결과. 목록에 있는 관광지는 모두 담긴다.
     */
    public Map<String, VisitTiming> resolve(List<AttractionSnapshot> attractions,
                                            DateMode dateMode, LocalDate visitDate, LocalDate today) {

        if (dateMode == null || attractions == null || attractions.isEmpty()) {
            return Map.of();
        }

        LocalDate supportedFrom = VisitTimingResolver.supportedFrom(today);
        LocalDate supportedTo = VisitTimingResolver.supportedTo(today);

        Map<String, RegionForecast> byLawdCode = new HashMap<>();
        Map<String, VisitTiming> result = new LinkedHashMap<>();

        for (AttractionSnapshot attraction : attractions) {
            // lawdCode 는 공급자가 준 두 값을 이어 붙인 것이라 형식이 어긋날 수 있다.
            // 어긋난 값으로 호출을 만들면 캐시 계층이 흡수하지 못하는 예외가 되어 500 이 된다.
            RegionForecast region = isLawdCode(attraction.lawdCode())
                    ? byLawdCode.computeIfAbsent(attraction.lawdCode(), this::fetchRegionForecast)
                    : RegionForecast.empty();

            AttractionForecast forecast = region.match(attraction);

            VisitTimingVerdict verdict = dateMode == DateMode.FIXED
                    ? VisitTimingResolver.resolveFixed(forecast, visitDate, today)
                    : VisitTimingResolver.resolveFlexible(forecast, today);

            result.put(attraction.contentId(), VisitTiming.of(
                    dateMode, verdict, supportedFrom, supportedTo,
                    region.dataStatus(), region.collectedAt(), TatsCnctrRateItemConverter.SOURCE));
        }

        return result;
    }

    /**
     * 관광지 상세용. 한 장소의 유연 모드 판정과 30일 일별 판정을 함께 돌려준다.
     *
     * <p>목록과 같은 캐시·같은 매칭·같은 경계를 쓰므로 상세의 그 날 판정이 목록의 확정 모드
     * 판정과 어긋나지 않는다.
     */
    public VisitTimingDetail resolveDetail(AttractionSnapshot attraction, LocalDate today) {
        LocalDate supportedFrom = VisitTimingResolver.supportedFrom(today);
        LocalDate supportedTo = VisitTimingResolver.supportedTo(today);

        RegionForecast region = isLawdCode(attraction.lawdCode())
                ? fetchRegionForecast(attraction.lawdCode())
                : RegionForecast.empty();

        AttractionForecast forecast = region.match(attraction);

        VisitTiming summary = VisitTiming.of(
                DateMode.FLEXIBLE,
                VisitTimingResolver.resolveFlexible(forecast, today),
                supportedFrom, supportedTo,
                region.dataStatus(), region.collectedAt(), TatsCnctrRateItemConverter.SOURCE);

        return new VisitTimingDetail(summary, VisitTimingResolver.resolveDaily(forecast, today));
    }

    /**
     * 시·군 하나의 예측을 모은다.
     *
     * <p>한 시·군의 행 수는 (관광지 수 × 30일) 이라 한 페이지에 담기지 않을 수 있다.
     * 첫 페이지의 totalCount 를 보고 남은 페이지를 이어 받되, 공급자 사정으로 행이 예상보다
     * 많을 때를 대비해 페이지 수에 상한을 둔다. 상한에서 잘린 관광지는 값을 지어내지 않고
     * 매칭되지 않은 채 정보 없음으로 남는다.
     */
    private RegionForecast fetchRegionForecast(String lawdCode) {
        List<TatsCnctrRateItem> items = new ArrayList<>();
        DataStatus status = null;
        LocalDateTime collectedAt = null;
        int totalPages = 1;

        for (int pageNo = 1; pageNo <= totalPages; pageNo++) {
            CachedResponse cached = fetchPage(lawdCode, pageNo);

            if (!cached.hasBody()) {
                // 첫 페이지부터 못 받으면 정보 없음, 뒤 페이지가 빠지면 일부만 모인 것이다.
                status = status == null ? DataStatus.NO_DATA : DataStatus.STALE;
                log.warn("TatsCnctrRate 응답을 받지 못했습니다. lawdCode={} pageNo={}", lawdCode, pageNo);
                break;
            }

            TatsCnctrRateResponse parsed;
            try {
                parsed = tatsCnctrRateClient.parse(cached.body());
            } catch (ExternalApiException e) {
                // 캐시에 남아 있던 본문이 더 이상 해석되지 않는 경우. 빈 예측으로 위장하지 않는다.
                log.warn("캐시된 TatsCnctrRate 응답을 해석하지 못했습니다. lawdCode={} pageNo={}",
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
            return RegionForecast.empty();
        }

        return RegionForecast.of(TatsCnctrRateItemConverter.convertAll(items), status, collectedAt);
    }

    private CachedResponse fetchPage(String lawdCode, int pageNo) {
        return cacheService.fetch(
                ApiProvider.TATS_CNCTR_RATE,
                tatsCnctrRateClient.tatsCnctrRatedListKey(lawdCode, pageNo),
                () -> tatsCnctrRateClient.tatsCnctrRatedListJson(lawdCode, pageNo),
                CACHE_TTL);
    }

    private int pageCount(int totalCount) {
        int rowsPerPage = tatsCnctrRateClient.rowsPerPage();
        int needed = (totalCount + rowsPerPage - 1) / rowsPerPage;

        return Math.max(1, Math.min(needed, tatsCnctrRateClient.maxPages()));
    }

    /** 법정동 시·군 코드는 숫자 5자리다. 형식이 맞아야 시·도 2자리를 잘라 낼 수 있다. */
    private static boolean isLawdCode(String value) {
        return value != null && value.length() == 5 && value.chars().allMatch(Character::isDigit);
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

    /**
     * 시·군 하나의 예측과 그 데이터 상태.
     *
     * <p>공급자가 관광지 식별자를 주지 않아 정규화한 이름으로 찾고, 양쪽에 좌표가 있으면
     * 거리로 확인한다. 이름이 같아도 좌표가 멀면 다른 장소로 보고 예측을 붙이지 않는다.
     */
    private record RegionForecast(Map<String, AttractionForecast> byNormalizedName,
                                  DataStatus dataStatus, LocalDateTime collectedAt) {

        private static RegionForecast empty() {
            return new RegionForecast(Map.of(), DataStatus.NO_DATA, null);
        }

        private static RegionForecast of(List<AttractionForecast> forecasts,
                                         DataStatus dataStatus, LocalDateTime collectedAt) {

            Map<String, AttractionForecast> byNormalizedName = new HashMap<>();

            for (AttractionForecast forecast : forecasts) {
                if (forecast.normalizedName() != null) {
                    byNormalizedName.putIfAbsent(forecast.normalizedName(), forecast);
                }
            }

            return new RegionForecast(byNormalizedName, dataStatus, collectedAt);
        }

        private AttractionForecast match(AttractionSnapshot attraction) {
            String normalized = PlaceNameNormalizer.normalize(attraction.name());

            if (normalized == null) {
                return null;
            }

            AttractionForecast candidate = byNormalizedName.get(normalized);

            if (candidate == null) {
                return null;
            }

            Double distance = Coordinates.distanceMeters(
                    attraction.latitude(), attraction.longitude(),
                    candidate.latitude(), candidate.longitude());

            // 좌표가 없으면 거리로 확인할 수 없다. 시·군이 이미 같으므로 이름 일치만으로 인정한다.
            return distance == null || distance <= MAX_MATCH_DISTANCE_METERS ? candidate : null;
        }
    }
}
