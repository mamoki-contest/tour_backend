package com.mamoki.tour.domain.currentaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.currentaccess.dto.ParkingLotView;
import com.mamoki.tour.domain.currentaccess.dto.ParkingView;
import com.mamoki.tour.domain.currentaccess.enums.ParkingCongestion;
import com.mamoki.tour.domain.currentaccess.enums.ParkingStatus;
import com.mamoki.tour.domain.currentaccess.service.ParkingAccessService;
import com.mamoki.tour.domain.currentaccess.service.ParkingSensorGuard;
import com.mamoki.tour.domain.parking.entity.ParkingLot;
import com.mamoki.tour.domain.parking.entity.ParkingLotSnapshot;
import com.mamoki.tour.domain.parking.repository.ParkingLotRepository;
import com.mamoki.tour.domain.parking.service.ParkingLotSnapshotService;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.infra.gnits.GnItsClient;
import com.mamoki.tour.infra.gnits.GnItsProperties;

/**
 * 관광지 주변 주차 여건. 저장한 실제 강릉시 응답으로 검증한다.
 *
 * <p>네 상태를 반드시 가른다. AVAILABLE(실시간 잔여면 있음) / STATIC_ONLY(주차장은 있으나
 * 실시간 모름) / NONE(반경 안에 주차장 없음) / NO_DATA(확인 못 함).
 */
class ParkingAccessServiceTest {

    /** 강릉중앙시장. 반경 1km 안에 실시간 주차장이 다섯 곳 있다. */
    private static final BigDecimal MARKET_LATITUDE = new BigDecimal("37.7528");
    private static final BigDecimal MARKET_LONGITUDE = new BigDecimal("128.8967");

    /** 속초 시내. 강릉시 실시간 주차장은 한 곳도 닿지 않는다. */
    private static final BigDecimal SOKCHO_LATITUDE = new BigDecimal("38.2070");
    private static final BigDecimal SOKCHO_LONGITUDE = new BigDecimal("128.5918");

    private GnItsClient client;
    private ExternalApiCacheService cacheService;
    private ParkingLotSnapshotService snapshotService;
    private ParkingLotRepository lotRepository;
    private ParkingSensorGuard sensorGuard;
    private ParkingAccessService service;

    private String parkInfo;
    private String parkRltm;

    @BeforeEach
    void setUp() throws Exception {
        client = new GnItsClient(new GnItsProperties(
                "http://example.invalid", "key", 100, 0, Duration.ZERO,
                Duration.ofSeconds(1), Duration.ofSeconds(1)));

        parkInfo = fixture("gnits-getParkInfo");
        parkRltm = fixture("gnits-getParkRltm");

        cacheService = Mockito.mock(ExternalApiCacheService.class);
        snapshotService = Mockito.mock(ParkingLotSnapshotService.class);
        lotRepository = Mockito.mock(ParkingLotRepository.class);
        sensorGuard = Mockito.mock(ParkingSensorGuard.class);

        givenRealtime(CachedResponse.available(parkInfo, LocalDateTime.now()),
                CachedResponse.available(parkRltm, LocalDateTime.now()));
        given(sensorGuard.stuckLotIds(any(), any())).willReturn(Set.of());
        given(snapshotService.findActive()).willReturn(Optional.empty());
        given(lotRepository.findWithinBox(any(), any(), any(), any(), any())).willReturn(List.of());

        service = new ParkingAccessService(
                client, cacheService, snapshotService, lotRepository, sensorGuard);
    }

    private static String fixture(String name) throws Exception {
        try (InputStream in = ParkingAccessServiceTest.class
                .getResourceAsStream("/fixtures/" + name + ".json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void givenRealtime(CachedResponse info, CachedResponse counts) {
        given(cacheService.fetch(any(), eq(client.parkInfoKey()), any(), any())).willReturn(info);
        given(cacheService.fetch(any(), eq(client.parkRltmKey()), any(), any())).willReturn(counts);
    }

    /** 표준데이터 스냅샷이 활성인 상태로 둔다. */
    private void givenCatalog(ParkingLot... lots) {
        given(snapshotService.findActive()).willReturn(Optional.of(
                ParkingLotSnapshot.builder().version("테스트").build()));
        given(lotRepository.findWithinBox(any(), any(), any(), any(), any()))
                .willReturn(List.of(lots));
    }

    private static ParkingLot catalogLot(String name, String latitude, String longitude,
                                         int capacity) {
        return ParkingLot.builder()
                .managementNumber(name)
                .name(name)
                .capacity(capacity)
                .latitude(new BigDecimal(latitude))
                .longitude(new BigDecimal(longitude))
                .dataBaseDate(LocalDate.of(2026, 4, 15))
                .providerName("강원특별자치도 강릉시")
                .build();
    }

    private ParkingView marketView() {
        return service.resolve(MARKET_LATITUDE, MARKET_LONGITUDE);
    }

    private static ParkingLotView lotNamed(ParkingView view, String name) {
        return view.lots().stream()
                .filter(lot -> lot.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("목록에 없습니다: " + name));
    }

    @Test
    @DisplayName("반경 안에 실시간 잔여면을 아는 주차장이 있으면 AVAILABLE 이다")
    void reportsAvailableWithRealtimeLots() {
        ParkingView view = marketView();

        assertThat(view.status()).isEqualTo(ParkingStatus.AVAILABLE);
        assertThat(view.dataStatus()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(view.observedAt()).isNotNull();
        assertThat(view.source()).isEqualTo("강릉시 교통정보 조회서비스");
        assertThat(view.lots()).isNotEmpty();
    }

    @Test
    @DisplayName("잔여면은 전체에서 점유를 뺀 값이고 혼잡은 잔여 비율로 끊는다")
    void computesAvailableLotsAndCongestion() {
        ParkingLotView lot = lotNamed(marketView(), "중앙시장제2공영주차장");

        assertThat(lot.totalLots()).isEqualTo(62);
        assertThat(lot.occupiedLots()).isEqualTo(24);
        assertThat(lot.availableLots()).isEqualTo(38);
        assertThat(lot.congestion()).isEqualTo(ParkingCongestion.PLENTY);
        assertThat(lot.observedAt()).isNotNull();
        assertThat(lot.distanceMeters()).isPositive();
    }

    @Test
    @DisplayName("점유가 전체와 같으면 만차로 내려간다")
    void reportsFullLot() {
        ParkingLotView lot = lotNamed(marketView(), "중앙시장제3공영주차장");

        assertThat(lot.availableLots()).isZero();
        assertThat(lot.congestion()).isEqualTo(ParkingCongestion.FULL);
    }

    @Test
    @DisplayName("반경 밖의 주차장은 담지 않는다")
    void excludesLotsOutsideRadius() {
        ParkingView view = marketView();

        assertThat(view.lots()).extracting(ParkingLotView::name)
                // 주문진해안주차타워는 15km 넘게 떨어져 있다.
                .doesNotContain("주문진해안주차타워", "강문제1공영주차장")
                .contains("성내동광장주차장", "중앙시장제1공영주차장");
        assertThat(view.lots()).allSatisfy(lot ->
                assertThat(lot.distanceMeters()).isLessThanOrEqualTo(ParkingAccessService.RADIUS_METERS));
    }

    @Test
    @DisplayName("센서가 고착된 주차장은 실시간에서 내리고 총 주차면까지만 남긴다")
    void demotesStuckLots() {
        given(sensorGuard.stuckLotIds(any(), any()))
                // 성내동광장(PLOT000001)·중앙시장제1(PLOT000004)·중앙시장제3(PLOT000013)
                .willReturn(Set.of("PLOT000001", "PLOT000004", "PLOT000013"));

        ParkingLotView stuck = lotNamed(marketView(), "성내동광장주차장");

        assertThat(stuck.availableLots()).isNull();
        assertThat(stuck.occupiedLots()).isNull();
        assertThat(stuck.congestion()).isNull();
        assertThat(stuck.observedAt()).isNull();
        // 이름·거리·총 주차면은 여전히 말할 수 있다.
        assertThat(stuck.totalLots()).isEqualTo(48);
        assertThat(stuck.hasRealtime()).isFalse();
    }

    @Test
    @DisplayName("반경 안의 실시간이 모두 고착이면 STATIC_ONLY 로 내려간다")
    void fallsBackToStaticOnlyWhenEveryLotIsStuck() {
        // 반경 1km 안의 여섯 곳. 성내동광장·중앙시장 제1·제2·제3·도심·동부시장이다.
        given(sensorGuard.stuckLotIds(any(), any())).willReturn(Set.of(
                "PLOT000001", "PLOT000004", "PLOT000005",
                "PLOT000007", "PLOT000013", "PLOT000014"));

        ParkingView view = marketView();

        assertThat(view.status()).isEqualTo(ParkingStatus.STATIC_ONLY);
        assertThat(view.lots()).isNotEmpty();
        assertThat(view.observedAt()).isNull();
        assertThat(view.lots()).allSatisfy(lot -> assertThat(lot.hasRealtime()).isFalse());
    }

    @Test
    @DisplayName("표준데이터에만 주차장이 있으면 STATIC_ONLY 다. 실시간으로 올리지 않는다")
    void reportsStaticOnlyOutsideRealtimeCoverage() {
        givenCatalog(catalogLot("속초해변 공영주차장", "38.2075", "128.5925", 120));

        ParkingView view = service.resolve(SOKCHO_LATITUDE, SOKCHO_LONGITUDE);

        assertThat(view.status()).isEqualTo(ParkingStatus.STATIC_ONLY);
        assertThat(view.source()).isEqualTo("전국주차장정보표준데이터");
        assertThat(view.observedAt()).isNull();

        ParkingLotView lot = lotNamed(view, "속초해변 공영주차장");
        assertThat(lot.totalLots()).isEqualTo(120);
        assertThat(lot.occupiedLots()).isNull();
        assertThat(lot.availableLots()).isNull();
        assertThat(lot.congestion()).isNull();
    }

    @Test
    @DisplayName("두 공급자를 확인했는데 반경 안에 주차장이 없으면 NONE 이다")
    void reportsNoneWhenNothingNearby() {
        givenCatalog();

        ParkingView view = service.resolve(SOKCHO_LATITUDE, SOKCHO_LONGITUDE);

        assertThat(view.status()).isEqualTo(ParkingStatus.NONE);
        assertThat(view.lots()).isEmpty();
        // 어느 공급자에게 물어본 결과인지 함께 밝힌다.
        assertThat(view.source())
                .contains("강릉시 교통정보 조회서비스")
                .contains("전국주차장정보표준데이터");
    }

    @Test
    @DisplayName("표준데이터를 적재하지 않았고 반경 안에 실시간도 없으면 NONE 이 아니라 NO_DATA 다")
    void reportsNoDataWhenCatalogNotImported() {
        ParkingView view = service.resolve(SOKCHO_LATITUDE, SOKCHO_LONGITUDE);

        // 한쪽 공급자를 아예 못 봤으므로 '없다' 고 단정하지 않는다.
        assertThat(view.status()).isEqualTo(ParkingStatus.NO_DATA);
    }

    @Test
    @DisplayName("두 공급자를 모두 확인하지 못하면 NO_DATA 다")
    void reportsNoDataWhenBothProvidersUnavailable() {
        givenRealtime(CachedResponse.noData(), CachedResponse.noData());

        ParkingView view = marketView();

        assertThat(view.status()).isEqualTo(ParkingStatus.NO_DATA);
        assertThat(view.dataStatus()).isEqualTo(DataStatus.NO_DATA);
        assertThat(view.lots()).isEmpty();
    }

    @Test
    @DisplayName("좌표가 없으면 다른 동네 주차장을 보여주지 않고 NO_DATA 로 둔다")
    void skipsWithoutCoordinates() {
        assertThat(service.resolve(null, MARKET_LONGITUDE).status()).isEqualTo(ParkingStatus.NO_DATA);
        assertThat(service.resolve(MARKET_LATITUDE, null).status()).isEqualTo(ParkingStatus.NO_DATA);

        Mockito.verifyNoInteractions(cacheService, snapshotService, lotRepository, sensorGuard);
    }

    @Test
    @DisplayName("현황만 못 받으면 주차장이 있다는 사실까지는 말한다")
    void keepsLotsWhenOnlyCountsMissing() {
        givenRealtime(CachedResponse.available(parkInfo, LocalDateTime.now()),
                CachedResponse.noData());

        ParkingView view = marketView();

        assertThat(view.status()).isEqualTo(ParkingStatus.STATIC_ONLY);
        assertThat(view.dataStatus()).isEqualTo(DataStatus.NO_DATA);
        assertThat(view.lots()).isNotEmpty();
        assertThat(view.lots()).allSatisfy(lot -> assertThat(lot.totalLots()).isNull());
    }

    @Test
    @DisplayName("최종 정상 데이터로 응답할 때는 상태를 STALE 로 밝힌다")
    void marksStaleRealtime() {
        LocalDateTime old = LocalDateTime.now().minusHours(2);
        givenRealtime(CachedResponse.available(parkInfo, LocalDateTime.now()),
                CachedResponse.stale(parkRltm, old));

        ParkingView view = marketView();

        assertThat(view.status()).isEqualTo(ParkingStatus.AVAILABLE);
        assertThat(view.dataStatus()).isEqualTo(DataStatus.STALE);
        assertThat(view.observedAt()).isEqualTo(old);
    }

    @Test
    @DisplayName("두 공급자가 같은 주차장을 가리키면 한 번만 담는다")
    void removesDuplicateLotAcrossProviders() {
        // 성내동광장주차장(37.7513261, 128.8948841) 의 표준데이터 짝. 실측상 12m 떨어져 있다.
        givenCatalog(catalogLot("성내동 광장 주차장(택시부광장 주차장)", "37.75143", "128.89482", 56));

        ParkingView view = marketView();

        assertThat(view.lots()).extracting(ParkingLotView::name)
                .contains("성내동광장주차장")
                .doesNotContain("성내동 광장 주차장(택시부광장 주차장)");
    }

    @Test
    @DisplayName("가까이 붙은 별개 주차장까지 지우지 않는다")
    void keepsDistinctNeighbouringLots() {
        givenCatalog(
                catalogLot("성내동 광장 주차장(택시부광장 주차장)", "37.75143", "128.89482", 56),
                catalogLot("명주동 주차장", "37.75299", "128.89302", 11));

        ParkingView view = marketView();

        assertThat(view.lots()).extracting(ParkingLotView::name).contains("명주동 주차장");
    }

    @Test
    @DisplayName("실시간을 아는 곳을 먼저 담고 각 묶음 안에서 가까운 순으로 담는다")
    void ordersRealtimeFirst() {
        givenCatalog(catalogLot("아주 가까운 표준데이터 주차장", "37.75281", "128.89671", 10));

        ParkingView view = marketView();

        assertThat(view.lots().get(0).hasRealtime()).isTrue();
        assertThat(view.lots()).last()
                .extracting(ParkingLotView::name)
                .isEqualTo("아주 가까운 표준데이터 주차장");
        assertThat(view.lots().stream().filter(ParkingLotView::hasRealtime).toList())
                .isSortedAccordingTo((a, b) ->
                        Integer.compare(a.distanceMeters(), b.distanceMeters()));
    }

    @Test
    @DisplayName("목록은 상한까지만 담되 상태 판정은 자르기 전 전체로 한다")
    void judgesStatusBeforeTruncating() {
        ParkingLot[] many = new ParkingLot[ParkingAccessService.MAX_LOTS + 3];

        for (int i = 0; i < many.length; i++) {
            // 관광지 코앞에 표준데이터 주차장을 잔뜩 둔다. 거리순이면 실시간이 전부 밀려난다.
            many[i] = catalogLot("표준데이터 주차장 " + i,
                    "37.7528" + i, "128.8967" + i, 10);
        }

        givenCatalog(many);

        ParkingView view = marketView();

        assertThat(view.lots()).hasSize(ParkingAccessService.MAX_LOTS);
        assertThat(view.status()).isEqualTo(ParkingStatus.AVAILABLE);
        assertThat(view.lots().get(0).hasRealtime()).isTrue();
        assertThat(view.source())
                .contains("강릉시 교통정보 조회서비스")
                .contains("전국주차장정보표준데이터");
    }

    @Test
    @DisplayName("공급자가 깨진 본문을 돌려줘도 예외가 컨트롤러까지 오르지 않는다")
    void absorbsBrokenProviderBody() {
        givenRealtime(CachedResponse.available("<html>gateway error</html>", LocalDateTime.now()),
                CachedResponse.available(parkRltm, LocalDateTime.now()));

        ParkingView view = marketView();

        assertThat(view.status()).isEqualTo(ParkingStatus.NO_DATA);
    }

    @Test
    @DisplayName("고착 판정에는 캐시가 본문을 수집한 시각을 넘긴다")
    void feedsGuardWithCollectedAt() {
        LocalDateTime collectedAt = LocalDateTime.of(2026, 9, 19, 21, 13);
        givenRealtime(CachedResponse.available(parkInfo, LocalDateTime.now()),
                CachedResponse.available(parkRltm, collectedAt));

        marketView();

        Mockito.verify(sensorGuard).stuckLotIds(anyList(), eq(collectedAt));
    }

    @Test
    @DisplayName("스로틀 뒤 캐시 폴백으로 받은 값도 그대로 쓴다")
    void usesCacheFallbackAfterThrottle() {
        // 재시도가 끝내 실패하면 캐시 계층이 최종 정상 데이터를 돌려준다.
        LocalDateTime old = LocalDateTime.now().minusMinutes(7);
        givenRealtime(CachedResponse.stale(parkInfo, old), CachedResponse.stale(parkRltm, old));

        ParkingView view = marketView();

        assertThat(view.status()).isEqualTo(ParkingStatus.AVAILABLE);
        assertThat(view.dataStatus()).isEqualTo(DataStatus.STALE);
        assertThat(lotNamed(view, "도심공영주차장").availableLots()).isEqualTo(22);
    }

    @Test
    @DisplayName("캐시 키가 오퍼레이션마다 달라 두 응답이 섞이지 않는다")
    void separatesCacheKeysPerOperation() {
        marketView();

        Mockito.verify(cacheService).fetch(any(), eq(client.parkInfoKey()), any(), any());
        Mockito.verify(cacheService).fetch(any(), eq(client.parkRltmKey()), any(), any());
        assertThat(client.parkInfoKey()).isNotEqualTo(client.parkRltmKey());
    }

    @Test
    @DisplayName("좌표가 없는 표준데이터 주차장은 반경 조회에 들어오지 않는다")
    void ignoresCatalogLotsWithoutCoordinates() {
        givenCatalog(ParkingLot.builder()
                .managementNumber("좌표없음")
                .name("좌표 없는 주차장")
                .capacity(20)
                .dataBaseDate(LocalDate.of(2026, 4, 15))
                .providerName("강원특별자치도 강릉시")
                .build());

        ParkingView view = service.resolve(SOKCHO_LATITUDE, SOKCHO_LONGITUDE);

        assertThat(view.status()).isEqualTo(ParkingStatus.NONE);
        assertThat(view.lots()).isEmpty();
    }

    @Test
    @DisplayName("혼잡 판정이 잔여 비율 경계에서 갈린다")
    void appliesCongestionThresholds() {
        assertThat(lotNamed(marketView(), "도심공영주차장").congestion())
                // 38면 중 22면 남음 = 57.9%
                .isEqualTo(ParkingCongestion.PLENTY);
        assertThat(ParkingCongestion.of(100, 9)).isEqualTo(ParkingCongestion.CROWDED);
        assertThat(ParkingCongestion.of(100, 10)).isEqualTo(ParkingCongestion.MODERATE);
    }
}
