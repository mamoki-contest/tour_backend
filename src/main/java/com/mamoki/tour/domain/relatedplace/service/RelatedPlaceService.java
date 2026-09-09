package com.mamoki.tour.domain.relatedplace.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.relatedplace.dto.RelatedPlace;
import com.mamoki.tour.domain.relatedplace.dto.RelatedPlaceRow;
import com.mamoki.tour.domain.relatedplace.dto.RelatedPlaces;
import com.mamoki.tour.domain.relatedplace.dto.RelatedPlacesView;
import com.mamoki.tour.domain.relatedplace.enums.RelatedPlaceKind;
import com.mamoki.tour.domain.relatedplace.support.RelatedPlaceClassifier;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;
import com.mamoki.tour.domain.visittiming.enums.DateMode;
import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;
import com.mamoki.tour.domain.visittiming.service.VisitTimingService;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.tarrltetar.TarRlteTarClient;
import com.mamoki.tour.infra.tarrltetar.TarRlteTarItemConverter;
import com.mamoki.tour.infra.tarrltetar.dto.TarRlteTarItem;
import com.mamoki.tour.infra.tarrltetar.dto.TarRlteTarResponse;

/**
 * 관광지 상세의 연관 장소를 대체지 후보와 함께 가기 좋은 곳으로 나눈다.
 *
 * <p>대체지 후보 자격은 두 가지를 모두 만족해야 한다. <b>원래 장소가 아닌 관광지</b>이고,
 * <b>유효한 방문 혼잡도 예측을 가진 곳</b>이다. 예측이 없는 곳을 대체지라고 말하면 한산하다는
 * 근거 없이 사람을 보내는 것이 된다. 그래서 예측 결측은 자격 미달로 다루고, 순위나 큐레이션이
 * 이 자격을 우회하지 못하게 한다.
 *
 * <p>공급자 장애는 캐시 계층이 흡수하므로 여기서 예외가 새어 나가지 않는다. 데이터를 얻지
 * 못하면 빈 목록이 아니라 그 사실을 담은 상태를 돌려준다.
 *
 * <p>알려진 비용: 연관 관광지가 여러 시·군에 걸치면 예측 조회가 시·군 수만큼 일어난다.
 * 24시간 캐시가 있어 두 번째 호출부터는 추가 호출이 없고, 후보 수에 상한을 두어 한 요청에서
 * 늘어날 수 있는 호출 수를 막는다.
 */
@Service
public class RelatedPlaceService {

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /**
     * 자격을 따질 대체지 후보 수의 상한.
     *
     * <p>공급자는 기준 관광지 하나당 연관 장소를 50곳까지 준다. 그중 관광지는 실측에서
     * 20곳 안팎이라 이 값이면 실제로는 잘리지 않는다. 공급자가 더 많이 내려줄 때
     * 예측 조회가 무한히 늘어나지 않도록 두는 상한이다. 잘린 후보는 자격 미달이 아니라
     * 판단 대상에서 빠진 것이며, 연관 순위가 낮은 쪽부터 빠진다.
     */
    private static final int MAX_ALTERNATIVE_CANDIDATES = 30;

    private static final Logger log = LoggerFactory.getLogger(RelatedPlaceService.class);

    private final TarRlteTarClient tarRlteTarClient;
    private final ExternalApiCacheService cacheService;
    private final VisitTimingService visitTimingService;

    public RelatedPlaceService(TarRlteTarClient tarRlteTarClient,
                               ExternalApiCacheService cacheService,
                               VisitTimingService visitTimingService) {
        this.tarRlteTarClient = tarRlteTarClient;
        this.cacheService = cacheService;
        this.visitTimingService = visitTimingService;
    }

    public RelatedPlaces resolve(AttractionSnapshot attraction) {
        return resolve(attraction, LocalDate.now());
    }

    /**
     * @param attraction 상세를 여는 관광지. 이 장소의 시·군으로 공급자를 조회한다.
     * @param today      지원 범위(오늘부터 30일)의 기준일
     */
    public RelatedPlaces resolve(AttractionSnapshot attraction, LocalDate today) {
        String baseYm = tarRlteTarClient.baseYm();

        if (!isLawdCode(attraction.lawdCode())) {
            // 시·군을 모르면 조회 키를 만들 수 없다. 다른 시·군으로 넘겨짚지 않는다.
            return RelatedPlaces.noData(baseYm);
        }

        RegionRelated region = fetchRegionRelated(attraction.lawdCode());

        if (region.rows().isEmpty()) {
            return new RelatedPlaces(
                    RelatedPlacesView.noData(baseYm), RelatedPlacesView.noData(baseYm));
        }

        String baseNormalized = PlaceNameNormalizer.normalize(attraction.name());
        List<RelatedPlaceRow> mine = region.rowsOf(baseNormalized);

        if (mine.isEmpty()) {
            // 공급자 응답은 받았지만 이 관광지가 연관 목록에 없다. 빈 목록이 아니라 그 사실을 알린다.
            // dataStatus 는 받아 온 응답의 것을 그대로 쓴다. 여기서 NO_DATA 로 덮으면
            // 공급자 데이터를 못 받았다고 거짓으로 알리게 된다.
            RelatedPlacesView notListed = RelatedPlacesView.of(
                    List.of(), false, region.dataStatus(), baseYm, region.collectedAt());

            return new RelatedPlaces(notListed, notListed);
        }

        return new RelatedPlaces(
                alternatives(mine, baseNormalized, region, baseYm, today),
                companions(mine, region, baseYm));
    }

    /**
     * 대체지 후보. 관광지이면서 원래 장소가 아니고, 유효한 예측을 가진 곳만 담는다.
     *
     * <p>자격 미달인 후보는 담지 않는다. 다만 후보 자체가 있었는지는 {@code status} 로 알려
     * "연관 데이터가 없었다" 와 "자격을 충족한 곳이 없었다" 를 구분한다.
     */
    private RelatedPlacesView alternatives(List<RelatedPlaceRow> mine, String baseNormalized,
                                           RegionRelated region, String baseYm, LocalDate today) {

        List<RelatedPlaceRow> candidates = mine.stream()
                .filter(row -> row.kind() == RelatedPlaceKind.ATTRACTION)
                .filter(row -> isDifferentPlace(row, baseNormalized))
                .sorted(Comparator.comparing(RelatedPlaceRow::rank,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_ALTERNATIVE_CANDIDATES)
                .toList();

        if (candidates.isEmpty()) {
            return RelatedPlacesView.of(List.of(), true, region.dataStatus(), baseYm, region.collectedAt());
        }

        Map<String, VisitTiming> timings = visitTimingService.resolve(
                candidates.stream().map(RelatedPlaceService::toSnapshot).toList(),
                DateMode.FLEXIBLE, null, today);

        List<RelatedPlace> eligible = new ArrayList<>();

        for (RelatedPlaceRow row : candidates) {
            VisitTiming timing = timings.get(candidateKey(row));

            // 예측이 없으면 자격 미달이다. 순위가 높아도 예외를 두지 않는다.
            if (hasUsableForecast(timing)) {
                eligible.add(toRelatedPlace(row, true, timing));
            }
        }

        return RelatedPlacesView.of(List.copyOf(eligible), true,
                region.dataStatus(), baseYm, region.collectedAt());
    }

    /** 함께 가기 좋은 곳. 음식점·숙박시설이며 대체지 자격을 따지지 않는다. */
    private RelatedPlacesView companions(List<RelatedPlaceRow> mine, RegionRelated region, String baseYm) {
        List<RelatedPlace> items = mine.stream()
                .filter(row -> RelatedPlaceClassifier.isCompanion(row.kind()))
                .sorted(Comparator.comparing(RelatedPlaceRow::rank,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(row -> toRelatedPlace(row, false, null))
                .toList();

        return RelatedPlacesView.of(items, true, region.dataStatus(), baseYm, region.collectedAt());
    }

    /**
     * 유효한 예측인지 판단한다.
     *
     * <p>{@code NO_DATA} 는 예측이 없거나 판정할 만큼 모이지 않은 것이고, {@code OUT_OF_RANGE} 는
     * 지원 범위 밖이다. 둘 다 "이 장소가 언제 한산한지 모른다" 는 뜻이라 대체지로 권할 수 없다.
     */
    private static boolean hasUsableForecast(VisitTiming timing) {
        return timing != null
                && timing.status() != VisitTimingStatus.NO_DATA
                && timing.status() != VisitTimingStatus.OUT_OF_RANGE;
    }

    /**
     * 원래 장소와 다른 곳인지 확인한다.
     *
     * <p>공급자가 기준 관광지를 자기 연관 목록에 넣어 주는 경우가 있다. 표기가 달라도 정규화하면
     * 같아지므로 정규화한 이름으로 가른다. 이름을 정규화할 수 없는 행은 같은 곳인지 확인할
     * 방법이 없어 후보에서 뺀다.
     */
    private static boolean isDifferentPlace(RelatedPlaceRow row, String baseNormalized) {
        return row.normalizedName() != null && !row.normalizedName().equals(baseNormalized);
    }

    private static RelatedPlace toRelatedPlace(RelatedPlaceRow row, boolean eligible, VisitTiming timing) {
        return new RelatedPlace(
                row.name(),
                row.kind(),
                row.categoryLarge(),
                row.categoryMiddle(),
                row.categorySmall(),
                row.lawdCode(),
                row.regionName(),
                row.rank(),
                eligible,
                timing);
    }

    /**
     * 예측 조회에 넘길 임시 스냅샷.
     *
     * <p>공급자가 표준 관광지 식별자를 주지 않으므로 시·군과 정규화한 이름으로 키를 만든다.
     * 예측 매칭도 시·군 안의 이름 일치로 이뤄지므로 이 키만으로 결과를 되찾을 수 있다.
     */
    private static AttractionSnapshot toSnapshot(RelatedPlaceRow row) {
        return new AttractionSnapshot(
                candidateKey(row), row.name(), null, null, null, null, null,
                row.lawdCode(), null, TarRlteTarItemConverter.SOURCE);
    }

    private static String candidateKey(RelatedPlaceRow row) {
        return row.lawdCode() + ":" + row.normalizedName();
    }

    /**
     * 시·군 하나의 연관 장소를 모은다.
     *
     * <p>한 시·군의 행 수는 (기준 관광지 수 × 연관 장소 수) 라 한 페이지에 담기지 않는다.
     * 첫 페이지의 totalCount 로 남은 페이지를 이어 받되, 페이지 수에 상한을 둔다.
     * 상한에서 잘린 관광지는 값을 지어내지 않고 연관 정보 없음으로 남는다.
     */
    private RegionRelated fetchRegionRelated(String lawdCode) {
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
            return RegionRelated.empty();
        }

        return RegionRelated.of(TarRlteTarItemConverter.convertAll(items), status, collectedAt);
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

    /** 시·군 하나의 연관 장소와 그 데이터 상태. 기준 관광지명으로 묶어 둔다. */
    private record RegionRelated(List<RelatedPlaceRow> rows, DataStatus dataStatus,
                                 LocalDateTime collectedAt) {

        private static RegionRelated empty() {
            return new RegionRelated(List.of(), DataStatus.NO_DATA, null);
        }

        private static RegionRelated of(List<RelatedPlaceRow> rows, DataStatus dataStatus,
                                        LocalDateTime collectedAt) {

            return new RegionRelated(rows, dataStatus, collectedAt);
        }

        private List<RelatedPlaceRow> rowsOf(String baseNormalizedName) {
            if (baseNormalizedName == null) {
                return List.of();
            }

            return rows.stream()
                    .filter(row -> baseNormalizedName.equals(row.baseNormalizedName()))
                    .toList();
        }
    }
}
