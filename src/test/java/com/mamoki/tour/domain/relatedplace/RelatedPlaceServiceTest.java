package com.mamoki.tour.domain.relatedplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.relatedplace.dto.RelatedPlace;
import com.mamoki.tour.domain.relatedplace.dto.RelatedPlaces;
import com.mamoki.tour.domain.relatedplace.enums.RelatedPlaceKind;
import com.mamoki.tour.domain.relatedplace.enums.RelatedPlacesStatus;
import com.mamoki.tour.domain.relatedplace.service.RelatedPlaceService;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;
import com.mamoki.tour.domain.visittiming.enums.DateMode;
import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;
import com.mamoki.tour.domain.visittiming.service.VisitTimingService;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.infra.tarrltetar.TarRlteTarClient;
import com.mamoki.tour.infra.tarrltetar.TarRlteTarProperties;

/**
 * 연관 장소를 대체지 후보와 함께 가기 좋은 곳으로 나누는 서비스.
 *
 * <p>여기서 지키려는 것은 세 가지다. 대체지 후보 자격(다른 장소 + 유효한 예측)을 무엇도
 * 우회하지 못하게 하는 것, 음식점·숙박시설을 대체지로 올리지 않는 것, 그리고 빈 목록의
 * 이유를 상태로 구분해 알리는 것이다.
 */
class RelatedPlaceServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);
    private static final String GANGNEUNG = "51150";
    private static final String SOKCHO = "51210";

    private RelatedPlaceService relatedPlaceService;
    private ExternalApiCacheService cacheService;
    private VisitTimingService visitTimingService;
    private TarRlteTarClient client;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(TarRlteTarClient.class);
        given(client.relatedListKey(anyString(), anyInt()))
                .willAnswer(call -> "areaBasedList1?signguCd="
                        + call.getArgument(0) + "&pageNo=" + call.getArgument(1));
        given(client.parse(anyString()))
                .willAnswer(call -> realClient().parse(call.getArgument(0)));
        given(client.rowsPerPage()).willReturn(1_000);
        given(client.maxPages()).willReturn(5);
        given(client.baseYm()).willReturn("202607");

        cacheService = Mockito.mock(ExternalApiCacheService.class);
        visitTimingService = Mockito.mock(VisitTimingService.class);

        // 기본은 모든 후보가 유효한 예측을 가진 상태. 결측은 각 테스트에서 따로 만든다.
        givenAllCandidatesForecast(VisitTimingStatus.LOW);

        relatedPlaceService = new RelatedPlaceService(client, cacheService, visitTimingService);
    }

    private TarRlteTarClient realClient() {
        return new TarRlteTarClient(new TarRlteTarProperties(
                "http://example.invalid", "key", "tour", "202607", 1_000, 5,
                Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("관광지만 대체지 후보가 되고 음식점·숙박시설은 함께 가기 좋은 곳으로 나뉜다")
    void splitsAttractionsFromCompanions() {
        givenResponse(body(
                row("경포해변", "정동진", "관광지", 1, GANGNEUNG),
                row("경포해변", "초당순두부", "음식", 2, GANGNEUNG),
                row("경포해변", "씨마크호텔", "숙박", 3, GANGNEUNG)));

        RelatedPlaces result = relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        assertThat(result.alternatives().items()).extracting(RelatedPlace::name)
                .containsExactly("정동진");
        assertThat(result.companions().items()).extracting(RelatedPlace::name)
                .containsExactly("초당순두부", "씨마크호텔");
        assertThat(result.companions().items()).extracting(RelatedPlace::kind)
                .containsExactly(RelatedPlaceKind.RESTAURANT, RelatedPlaceKind.LODGING);
    }

    @Test
    @DisplayName("유효한 예측이 없는 관광지는 연관 순위가 높아도 대체지 후보에서 빠진다")
    void excludesAttractionWithoutForecast() {
        givenResponse(body(
                row("경포해변", "정동진", "관광지", 1, GANGNEUNG),
                row("경포해변", "주문진항", "관광지", 2, GANGNEUNG)));

        // 1순위인 정동진에 예측이 없다. 순위가 자격을 우회하지 못해야 한다.
        givenForecasts(Map.of(
                GANGNEUNG + ":정동진", VisitTimingStatus.NO_DATA,
                GANGNEUNG + ":주문진항", VisitTimingStatus.NORMAL));

        RelatedPlaces result = relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        assertThat(result.alternatives().items()).extracting(RelatedPlace::name)
                .containsExactly("주문진항");
    }

    @Test
    @DisplayName("대체지 후보에는 자격의 근거인 예측이 함께 담긴다")
    void carriesForecastAsEvidence() {
        givenResponse(body(row("경포해변", "정동진", "관광지", 1, GANGNEUNG)));
        givenForecasts(Map.of(GANGNEUNG + ":정동진", VisitTimingStatus.LOW));

        RelatedPlace alternative = relatedPlaceService
                .resolve(attraction("경포해변", GANGNEUNG), TODAY)
                .alternatives().items().get(0);

        assertThat(alternative.eligibleAsAlternative()).isTrue();
        assertThat(alternative.visitTiming()).isNotNull();
        assertThat(alternative.visitTiming().status()).isEqualTo(VisitTimingStatus.LOW);
    }

    @Test
    @DisplayName("함께 가기 좋은 곳은 대체지 자격을 따지지 않고 예측도 조회하지 않는다")
    void companionsAreNeverAlternatives() {
        givenResponse(body(
                row("경포해변", "초당순두부", "음식", 1, GANGNEUNG),
                row("경포해변", "씨마크호텔", "숙박", 2, GANGNEUNG)));

        RelatedPlaces result = relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        assertThat(result.companions().items()).allSatisfy(place -> {
            assertThat(place.eligibleAsAlternative()).isFalse();
            assertThat(place.visitTiming()).isNull();
        });
        // 후보가 될 수 없는 곳까지 예측을 부르면 쓸모없는 외부 호출이 늘어난다.
        verify(visitTimingService, never()).resolve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("공급자가 기준 관광지 자신을 연관 목록에 넣어도 대체지 후보로 올리지 않는다")
    void excludesSamePlace() {
        // 표기가 달라도 정규화하면 같은 곳이다. 자기 자신은 대신할 장소가 될 수 없다.
        givenResponse(body(
                row("경포해변", "경포 해변", "관광지", 1, GANGNEUNG),
                row("경포해변", "정동진", "관광지", 2, GANGNEUNG)));

        RelatedPlaces result = relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        assertThat(result.alternatives().items()).extracting(RelatedPlace::name)
                .containsExactly("정동진");
    }

    @Test
    @DisplayName("모르는 대분류는 어느 묶음에도 넣지 않는다")
    void doesNotGuessUnknownCategory() {
        givenResponse(body(row("경포해변", "정체불명", "레저", 1, GANGNEUNG)));

        RelatedPlaces result = relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        assertThat(result.alternatives().items()).isEmpty();
        assertThat(result.companions().items()).isEmpty();
        // 연관 데이터는 받았으므로 정보 없음이 아니라 자격 미달이다.
        assertThat(result.alternatives().status()).isEqualTo(RelatedPlacesStatus.NONE_QUALIFIED);
    }

    @Test
    @DisplayName("연관 장소는 있으나 자격을 충족한 곳이 없으면 정보 없음과 구분해 알린다")
    void distinguishesNoneQualifiedFromNoData() {
        givenResponse(body(row("경포해변", "정동진", "관광지", 1, GANGNEUNG)));
        givenForecasts(Map.of(GANGNEUNG + ":정동진", VisitTimingStatus.NO_DATA));

        RelatedPlaces result = relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        assertThat(result.alternatives().items()).isEmpty();
        assertThat(result.alternatives().status()).isEqualTo(RelatedPlacesStatus.NONE_QUALIFIED);
        assertThat(result.alternatives().dataStatus()).isEqualTo(DataStatus.AVAILABLE);
    }

    @Test
    @DisplayName("공급자 데이터를 못 받으면 빈 목록이 아니라 정보 없음으로 알린다")
    void reportsNoRelatedDataWhenProviderFails() {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.noData());

        RelatedPlaces result = relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        assertThat(result.alternatives().status()).isEqualTo(RelatedPlacesStatus.NO_RELATED_DATA);
        assertThat(result.companions().status()).isEqualTo(RelatedPlacesStatus.NO_RELATED_DATA);
        assertThat(result.alternatives().dataStatus()).isEqualTo(DataStatus.NO_DATA);
    }

    @Test
    @DisplayName("이 관광지가 공급자 연관 목록에 없으면 자격 미달이 아니라 정보 없음이다")
    void reportsNoRelatedDataWhenAttractionAbsent() {
        givenResponse(body(row("다른관광지", "정동진", "관광지", 1, GANGNEUNG)));

        RelatedPlaces result = relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        assertThat(result.alternatives().status()).isEqualTo(RelatedPlacesStatus.NO_RELATED_DATA);
        assertThat(result.companions().status()).isEqualTo(RelatedPlacesStatus.NO_RELATED_DATA);
    }

    @Test
    @DisplayName("연관 목록에 없는 것과 공급자 데이터를 못 받은 것은 dataStatus 로 구분된다")
    void separatesNotListedFromProviderFailure() {
        // 응답은 정상으로 받았고 이 관광지만 목록에 없는 경우다. 데이터 신선도까지
        // NO_DATA 로 덮으면 공급자 데이터를 못 받았다고 거짓으로 알리게 된다.
        LocalDateTime collectedAt = LocalDateTime.of(2026, 9, 8, 3, 0);
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available(
                        body(row("다른관광지", "정동진", "관광지", 1, GANGNEUNG)), collectedAt));

        RelatedPlaces notListed = relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        assertThat(notListed.alternatives().dataStatus()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(notListed.alternatives().collectedAt()).isEqualTo(collectedAt);

        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.noData());

        RelatedPlaces failed = relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        assertThat(failed.alternatives().dataStatus()).isEqualTo(DataStatus.NO_DATA);
        assertThat(failed.alternatives().collectedAt()).isNull();
    }

    @Test
    @DisplayName("법정동 코드 형식이 어긋나면 호출하지 않고 정보 없음으로 남긴다")
    void skipsCallWhenLawdCodeBroken() {
        RelatedPlaces result = relatedPlaceService.resolve(attraction("경포해변", "51"), TODAY);

        assertThat(result.alternatives().status()).isEqualTo(RelatedPlacesStatus.NO_RELATED_DATA);
        verify(cacheService, never()).fetch(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("연관 장소가 다른 시·군이어도 그대로 담고 시·군을 밝힌다")
    void keepsRelatedPlaceInAnotherRegion() {
        givenResponse(body(row("경포해변", "속초해수욕장", "관광지", 1, SOKCHO)));
        givenForecasts(Map.of(SOKCHO + ":속초해수욕장", VisitTimingStatus.LOW));

        RelatedPlace alternative = relatedPlaceService
                .resolve(attraction("경포해변", GANGNEUNG), TODAY)
                .alternatives().items().get(0);

        assertThat(alternative.lawdCode()).isEqualTo(SOKCHO);
        assertThat(alternative.regionName()).isNotNull();
    }

    @Test
    @DisplayName("두 묶음 모두 공급자 연관 순위 오름차순으로 담는다")
    void ordersByProviderRank() {
        givenResponse(body(
                row("경포해변", "주문진항", "관광지", 5, GANGNEUNG),
                row("경포해변", "정동진", "관광지", 1, GANGNEUNG),
                row("경포해변", "씨마크호텔", "숙박", 4, GANGNEUNG),
                row("경포해변", "초당순두부", "음식", 2, GANGNEUNG)));

        RelatedPlaces result = relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        assertThat(result.alternatives().items()).extracting(RelatedPlace::name)
                .containsExactly("정동진", "주문진항");
        assertThat(result.companions().items()).extracting(RelatedPlace::name)
                .containsExactly("초당순두부", "씨마크호텔");
    }

    @Test
    @DisplayName("최종 정상 데이터로 응답하면 상태와 기준 시점을 함께 알린다")
    void reportsStaleWithCollectedAt() {
        LocalDateTime collectedAt = LocalDateTime.of(2026, 9, 1, 3, 0);
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.stale(
                        body(row("경포해변", "정동진", "관광지", 1, GANGNEUNG)), collectedAt));

        RelatedPlaces result = relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        assertThat(result.alternatives().dataStatus()).isEqualTo(DataStatus.STALE);
        assertThat(result.alternatives().collectedAt()).isEqualTo(collectedAt);
        assertThat(result.alternatives().baseYm()).isEqualTo("202607");
        assertThat(result.alternatives().source()).isEqualTo("TarRlteTarService1");
    }

    @Test
    @DisplayName("같은 시·군이면 연관 장소가 여러 곳이어도 공급자는 한 번만 부른다")
    void callsProviderOncePerRegion() {
        givenResponse(body(
                row("경포해변", "정동진", "관광지", 1, GANGNEUNG),
                row("경포해변", "주문진항", "관광지", 2, GANGNEUNG),
                row("경포해변", "초당순두부", "음식", 3, GANGNEUNG)));

        relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        verify(cacheService, times(1)).fetch(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("예측 조회는 후보를 모아 한 번만 부른다")
    void resolvesForecastsInOneCall() {
        givenResponse(body(
                row("경포해변", "정동진", "관광지", 1, GANGNEUNG),
                row("경포해변", "속초해수욕장", "관광지", 2, SOKCHO)));

        relatedPlaceService.resolve(attraction("경포해변", GANGNEUNG), TODAY);

        // 후보마다 부르면 시·군 캐시를 공유하지 못한다. 한 번에 넘겨 시·군 수만큼만 부르게 한다.
        verify(visitTimingService, times(1))
                .resolve(any(), any(DateMode.class), any(), any());
    }

    private void givenResponse(String body) {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available(body, LocalDateTime.now()));
    }

    /** 넘어온 후보를 모두 같은 상태로 돌려준다. 자격 판정만 보는 테스트에서 쓴다. */
    private void givenAllCandidatesForecast(VisitTimingStatus status) {
        given(visitTimingService.resolve(any(), any(DateMode.class), any(), any()))
                .willAnswer(call -> {
                    List<AttractionSnapshot> snapshots = call.getArgument(0);
                    return snapshots.stream().collect(Collectors.toMap(
                            AttractionSnapshot::contentId, snapshot -> timing(status)));
                });
    }

    /** 후보 키(시·군:정규화한 이름)별로 다른 상태를 돌려준다. */
    private void givenForecasts(Map<String, VisitTimingStatus> byCandidateKey) {
        given(visitTimingService.resolve(any(), any(DateMode.class), any(), any()))
                .willAnswer(call -> {
                    List<AttractionSnapshot> snapshots = call.getArgument(0);
                    Map<String, VisitTiming> result = new HashMap<>();

                    for (AttractionSnapshot snapshot : snapshots) {
                        VisitTimingStatus status = byCandidateKey.get(snapshot.contentId());

                        if (status != null) {
                            result.put(snapshot.contentId(), timing(status));
                        }
                    }

                    return result;
                });
    }

    private static VisitTiming timing(VisitTimingStatus status) {
        return new VisitTiming(DateMode.FLEXIBLE, status, null,
                status == VisitTimingStatus.NO_DATA ? null : TODAY.plusDays(3), 30,
                TODAY, TODAY.plusDays(29), DataStatus.AVAILABLE,
                LocalDateTime.of(2026, 9, 8, 3, 0), "TatsCnctrRateService");
    }

    private static AttractionSnapshot attraction(String name, String lawdCode) {
        return new AttractionSnapshot(
                "1", name, null, null, null, null, "12", lawdCode, null, "KorService2");
    }

    private static String row(String baseName, String relatedName,
                              String categoryLarge, int rank, String lawdCode) {

        return """
                {"baseYm":"202607","tAtsCd":"base","tAtsNm":"%s","areaCd":"51",\
                "areaNm":"강원특별자치도","signguCd":"51150","signguNm":"강릉시",\
                "rlteTatsCd":"related","rlteTatsNm":"%s","rlteRegnCd":"51",\
                "rlteRegnNm":"강원특별자치도","rlteSignguCd":"%s","rlteSignguNm":"시군",\
                "rlteCtgryLclsNm":"%s","rlteCtgryMclsNm":"중분류","rlteCtgrySclsNm":"소분류",\
                "rlteRank":"%d"}"""
                .formatted(baseName, relatedName, lawdCode, categoryLarge, rank);
    }

    /** 공급자 응답을 그대로 흉내 낸 JSON. 한 행이 (기준 관광지 1곳 × 연관 장소 1곳) 이다. */
    private static String body(String... rows) {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},\
                "body":{"items":{"item":[%s]},"numOfRows":1000,"pageNo":1,"totalCount":%d}}}"""
                .formatted(Arrays.stream(rows).collect(Collectors.joining(",")), rows.length);
    }
}
