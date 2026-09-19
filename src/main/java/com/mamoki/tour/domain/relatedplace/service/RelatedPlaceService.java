package com.mamoki.tour.domain.relatedplace.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.placemapping.service.PlaceMatcher;
import com.mamoki.tour.domain.relatedplace.dto.RegionRelatedPlaces;
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
import com.mamoki.tour.infra.tarrltetar.TarRlteTarItemConverter;

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
 * <p>대체지 큐레이션은 자격 판정이 끝난 뒤 {@code AlternativeCurationService} 가 따로
 * 적용한다(#54). 순서를 바꾸면 큐레이션이 자격을 만들어 줄 수 있게 되므로 뒤로 고정한다.
 *
 * <p>알려진 비용: 연관 관광지가 여러 시·군에 걸치면 예측 조회가 시·군 수만큼 일어난다.
 * 24시간 캐시가 있어 두 번째 호출부터는 추가 호출이 없고, 후보 수에 상한을 두어 한 요청에서
 * 늘어날 수 있는 호출 수를 막는다.
 */
@Service
public class RelatedPlaceService {

    /**
     * 자격을 따질 대체지 후보 수의 상한.
     *
     * <p>공급자는 기준 관광지 하나당 연관 장소를 50곳까지 준다. 그중 관광지는 실측에서
     * 20곳 안팎이라 이 값이면 실제로는 잘리지 않는다. 공급자가 더 많이 내려줄 때
     * 예측 조회가 무한히 늘어나지 않도록 두는 상한이다. 잘린 후보는 자격 미달이 아니라
     * 판단 대상에서 빠진 것이며, 연관 순위가 낮은 쪽부터 빠진다.
     */
    private static final int MAX_ALTERNATIVE_CANDIDATES = 30;

    private final RelatedPlaceRegionLoader regionLoader;
    private final VisitTimingService visitTimingService;
    private final AlternativeCurationService curationService;
    private final PlaceMatcher placeMatcher;

    public RelatedPlaceService(RelatedPlaceRegionLoader regionLoader,
                               VisitTimingService visitTimingService,
                               AlternativeCurationService curationService,
                               PlaceMatcher placeMatcher) {
        this.regionLoader = regionLoader;
        this.visitTimingService = visitTimingService;
        this.curationService = curationService;
        this.placeMatcher = placeMatcher;
    }

    public RelatedPlaces resolve(AttractionSnapshot attraction) {
        return resolve(attraction, LocalDate.now());
    }

    /**
     * @param attraction 상세를 여는 관광지. 이 장소의 시·군으로 공급자를 조회한다.
     * @param today      지원 범위의 기준일. 범위 자체는 상수가 아니라 공급자 응답의 날짜에서
     *                   나온다({@code ForecastWindow}). 기준일이 하루 뒤처진 날은 29일,
     *                   그렇지 않은 날은 30일이며, 상한은 이 날부터 30일이다.
     */
    public RelatedPlaces resolve(AttractionSnapshot attraction, LocalDate today) {
        String baseYm = regionLoader.baseYm();

        if (!isLawdCode(attraction.lawdCode())) {
            // 시·군을 모르면 조회 키를 만들 수 없다. 다른 시·군으로 넘겨짚지 않는다.
            return RelatedPlaces.noData(baseYm);
        }

        RegionRelatedPlaces region = regionLoader.load(attraction.lawdCode());

        if (region.rows().isEmpty()) {
            return new RelatedPlaces(
                    RelatedPlacesView.noData(baseYm), RelatedPlacesView.noData(baseYm));
        }

        // 공급자가 이 관광지를 다른 이름으로 적어 둔 경우가 대부분이다(경포해변 vs 경포해수욕장).
        // 카탈로그 이름이 먼저고, 매핑 표(#55)의 확정 행이 그 뒤를 잇는다.
        Set<String> aliases = placeMatcher.normalizedAliases(
                MappingSource.RELATED_PLACE, attraction.contentId(), attraction.name());
        List<RelatedPlaceRow> mine = region.rowsOfAny(aliases);

        if (mine.isEmpty()) {
            // 공급자 응답은 받았지만 이 관광지가 연관 목록에 없다. 빈 목록이 아니라 그 사실을 알린다.
            // dataStatus 는 받아 온 응답의 것을 그대로 쓴다. 여기서 NO_DATA 로 덮으면
            // 공급자 데이터를 못 받았다고 거짓으로 알리게 된다.
            RelatedPlacesView notListed = RelatedPlacesView.of(
                    List.of(), false, region.dataStatus(), baseYm, region.collectedAt());

            return new RelatedPlaces(notListed, notListed);
        }

        return new RelatedPlaces(
                alternatives(mine, attraction.contentId(), aliases, region, baseYm, today),
                companions(mine, region, baseYm));
    }

    /**
     * 대체지 후보. 관광지이면서 원래 장소가 아니고, 유효한 예측을 가진 곳만 담는다.
     *
     * <p>자격 미달인 후보는 담지 않는다. 다만 후보 자체가 있었는지는 {@code status} 로 알려
     * "연관 데이터가 없었다" 와 "자격을 충족한 곳이 없었다" 를 구분한다.
     *
     * <p>큐레이션은 자격을 모두 따진 <b>뒤</b>에 마지막으로 적용한다. 자격 미달 후보는
     * 이 지점까지 오지 않으므로 큐레이션이 되살릴 수 없다.
     */
    private RelatedPlacesView alternatives(List<RelatedPlaceRow> mine, String baseContentId,
                                           Set<String> baseAliases, RegionRelatedPlaces region,
                                           String baseYm, LocalDate today) {

        List<RelatedPlaceRow> candidates = mine.stream()
                .filter(row -> row.kind() == RelatedPlaceKind.ATTRACTION)
                .filter(row -> isDifferentPlace(row, baseAliases))
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

        List<RelatedPlace> curated = curationService.curate(baseContentId, List.copyOf(eligible));

        return RelatedPlacesView.of(curated, true,
                region.dataStatus(), baseYm, region.collectedAt());
    }

    /** 함께 가기 좋은 곳. 음식점·숙박시설이며 대체지 자격을 따지지 않는다. */
    private RelatedPlacesView companions(List<RelatedPlaceRow> mine, RegionRelatedPlaces region,
                                         String baseYm) {
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
     *
     * <p>기준 관광지를 가리키는 이름이 매핑 표에 더 있으면 그 이름들도 같은 곳으로 본다.
     * 매핑으로 연관 목록을 찾아 놓고 그 목록 안의 자기 자신은 놓치면, 원래 장소가 자기
     * 대체지로 올라온다.
     */
    private static boolean isDifferentPlace(RelatedPlaceRow row, Set<String> baseAliases) {
        return row.normalizedName() != null && !baseAliases.contains(row.normalizedName());
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

    /** 법정동 시·군 코드는 숫자 5자리다. 형식이 맞아야 공급자 조회 키를 만들 수 있다. */
    private static boolean isLawdCode(String value) {
        return value != null && value.length() == 5 && value.chars().allMatch(Character::isDigit);
    }
}
