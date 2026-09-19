package com.mamoki.tour.domain.currentaccess.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.support.Coordinates;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.currentaccess.dto.ParkingLotView;
import com.mamoki.tour.domain.currentaccess.dto.ParkingView;
import com.mamoki.tour.domain.currentaccess.enums.ParkingCongestion;
import com.mamoki.tour.domain.currentaccess.enums.ParkingStatus;
import com.mamoki.tour.domain.parking.entity.ParkingLot;
import com.mamoki.tour.domain.parking.entity.ParkingLotSnapshot;
import com.mamoki.tour.domain.parking.repository.ParkingLotRepository;
import com.mamoki.tour.domain.parking.service.ParkingLotSnapshotService;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.gnits.GnItsClient;
import com.mamoki.tour.infra.gnits.GnItsParkConverter;
import com.mamoki.tour.infra.gnits.dto.RealtimeParkingLot;

/**
 * 관광지 주변 주차 여건을 모은다.
 *
 * <p>두 공급자를 겹쳐 쓴다. 강릉시 교통정보 조회서비스가 실시간 잔여면을 주지만 강릉 13곳
 * 뿐이고, 전국주차장정보표준데이터는 강원 18개 시·군 1,398곳을 덮지만 실시간이 없다.
 * 둘 중 하나로는 "주차장이 아예 없는 곳" 과 "주차장은 있으나 지금 상황을 모르는 곳" 을
 * 가를 수 없다.
 *
 * <p>정적 정보를 현재 상태로 위장하지 않는다. 표준데이터만 있는 주차장은 실시간 잔여면이
 * 없으므로 {@code AVAILABLE} 로 올리지 않고 {@code STATIC_ONLY} 에 둔다.
 *
 * <p>좌표가 없으면 조회하지 않는다. 어디 주변을 물을지 모르는데 임의의 좌표로 물으면
 * 다른 동네의 주차장을 그 관광지의 것으로 보여주게 된다.
 */
@Service
public class ParkingAccessService {

    public static final String STANDARD_SOURCE = "전국주차장정보표준데이터";

    /**
     * 관광지 좌표에서 이 거리 안의 주차장만 담는다.
     *
     * <p>도로 소통({@code ITS_HALF_SPAN} 약 555m)보다 넓다. 도로는 관광지 앞을 지나는 길을
     * 찾는 일이라 좁아야 하지만, 주차장은 세워 두고 걸어갈 수 있으면 된다. 실측에서
     * 경포해수욕장의 가장 가까운 실시간 주차장(강문제2공영주차장)이 955m 였다. 여기서 더
     * 좁히면 경포 일대가 통째로 STATIC_ONLY 가 되고, 더 넓히면 걸어갈 수 없는 주차장이 섞인다.
     */
    public static final int RADIUS_METERS = 1_000;

    /** 응답에 담을 주차장 수. 상세 한 화면에 줄 세울 수 있는 만큼이다. */
    public static final int MAX_LOTS = 8;

    /**
     * 두 공급자가 같은 주차장을 가리킨다고 볼 거리.
     *
     * <p>실측에서 실시간 주차장 13곳 중 표준데이터에도 있는 곳들은 1~81m 안에 짝이 있었다.
     * 실시간 하나당 가장 가까운 표준데이터 한 곳만 지운다. 거리로만 뭉뚱그리면 중앙시장
     * 제2·제3처럼 51m 떨어진 별개 주차장이 함께 사라진다.
     */
    public static final int SAME_LOT_METERS = 100;

    /** 이름·좌표·운영시각은 거의 바뀌지 않는다. 다른 정적 신호와 같은 24시간이다. */
    private static final Duration INFO_CACHE_TTL = Duration.ofHours(24);

    /** 지금 이 순간의 점유라 도로 소통과 같은 5분이다. */
    private static final Duration REALTIME_CACHE_TTL = Duration.ofMinutes(5);

    /** 위도 1도의 대략적인 거리(m). 사각형으로 1차로 좁히는 데만 쓴다. */
    private static final double METERS_PER_DEGREE = 111_320;

    private static final Logger log = LoggerFactory.getLogger(ParkingAccessService.class);

    private final GnItsClient gnItsClient;
    private final ExternalApiCacheService cacheService;
    private final ParkingLotSnapshotService snapshotService;
    private final ParkingLotRepository lotRepository;
    private final ParkingSensorGuard sensorGuard;

    public ParkingAccessService(GnItsClient gnItsClient,
                                ExternalApiCacheService cacheService,
                                ParkingLotSnapshotService snapshotService,
                                ParkingLotRepository lotRepository,
                                ParkingSensorGuard sensorGuard) {
        this.gnItsClient = gnItsClient;
        this.cacheService = cacheService;
        this.snapshotService = snapshotService;
        this.lotRepository = lotRepository;
        this.sensorGuard = sensorGuard;
    }

    public ParkingView resolve(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return ParkingView.noData();
        }

        Realtime realtime = loadRealtime();
        Optional<ParkingLotSnapshot> snapshot = snapshotService.findActive();

        if (!realtime.reachable() && snapshot.isEmpty()) {
            // 두 공급자를 모두 확인하지 못했다. '주차장 없음' 이 아니라 '모름' 이다.
            return ParkingView.noData();
        }

        List<ParkingLotView> fromProvider = nearbyRealtime(realtime, latitude, longitude);
        List<ParkingLotView> fromCatalog = withoutDuplicates(
                fromProvider, nearbyCatalog(snapshot, latitude, longitude));

        List<ParkingLotView> all = order(fromProvider, fromCatalog);

        if (all.isEmpty()) {
            return realtime.reachable() && snapshot.isPresent()
                    ? ParkingView.none(realtime.dataStatus(), consultedSources(realtime, snapshot))
                    : ParkingView.noData();
        }

        // 상태 판정은 잘라내기 전 전체 목록으로 한다. 잘림 때문에 AVAILABLE 이 STATIC_ONLY 로
        // 내려가면 안 된다. 반면 출처는 실제로 내려보내는 목록에서 뽑는다. 목록에 없는
        // 공급자를 출처로 적으면 사용자가 화면에서 확인할 수 없는 이름을 보게 된다.
        boolean anyRealtime = all.stream().anyMatch(ParkingLotView::hasRealtime);
        List<ParkingLotView> shown = all.stream().limit(MAX_LOTS).toList();

        return new ParkingView(
                anyRealtime ? ParkingStatus.AVAILABLE : ParkingStatus.STATIC_ONLY,
                realtime.dataStatus(),
                shown,
                anyRealtime ? realtime.observedAt() : null,
                sourcesOf(shown));
    }

    /**
     * 실시간 공급자에게 두 번 묻는다.
     *
     * <p>기본정보 없이 현황만으로는 좌표를 몰라 어느 관광지 주변인지 가릴 수 없다. 그래서
     * 기본정보를 받지 못하면 실시간 쪽은 통째로 확인 실패로 본다. 반대로 현황만 없으면
     * 주차장이 있다는 사실까지는 말할 수 있다.
     */
    private Realtime loadRealtime() {
        CachedResponse info = cacheService.fetch(
                ApiProvider.GN_ITS_PARKING,
                gnItsClient.parkInfoKey(),
                gnItsClient::parkInfoJson,
                INFO_CACHE_TTL);

        if (!info.hasBody()) {
            return Realtime.unreachable();
        }

        CachedResponse counts = cacheService.fetch(
                ApiProvider.GN_ITS_PARKING,
                gnItsClient.parkRltmKey(),
                gnItsClient::parkRltmJson,
                REALTIME_CACHE_TTL);

        try {
            List<RealtimeParkingLot> lots = GnItsParkConverter.join(
                    gnItsClient.parseParkInfo(info.body()).items(),
                    counts.hasBody() ? gnItsClient.parseParkRltm(counts.body()).items() : List.of());

            return new Realtime(true, lots,
                    counts.hasBody() ? counts.status() : DataStatus.NO_DATA,
                    counts.collectedAt());

        } catch (ExternalApiException e) {
            log.warn("캐시된 강릉시 주차 응답을 해석하지 못했습니다.", e);
            return Realtime.unreachable();
        }
    }

    /**
     * 고착된 센서는 여기서 걸러 실시간에서 내린다. 값을 고치지 않고 쓰지 않을 뿐이다.
     *
     * <p><b>반경 안 주차장을 먼저 추린 뒤에 고착 장부를 갱신한다.</b> 장부 갱신은 쓰기
     * 트랜잭션이라, 순서를 뒤집으면 반경 안에 주차장이 하나도 없는 관광지(강원 대부분이다)를
     * 열 때마다 쓸 일이 없는 쓰기 트랜잭션이 열린다. 조회가 쓰기를 부르는 자리는 꼭 필요한
     * 만큼만 있어야 한다.
     */
    private List<ParkingLotView> nearbyRealtime(Realtime realtime,
                                                BigDecimal latitude, BigDecimal longitude) {
        if (realtime.lots().isEmpty()) {
            return List.of();
        }

        List<Nearby> nearby = new ArrayList<>();

        for (RealtimeParkingLot lot : realtime.lots()) {
            Double distance = Coordinates.distanceMeters(
                    latitude, longitude, lot.latitude(), lot.longitude());

            if (distance != null && distance <= RADIUS_METERS) {
                nearby.add(new Nearby(lot, distance));
            }
        }

        if (nearby.isEmpty()) {
            return List.of();
        }

        Set<String> stuck = sensorGuard.stuckLotIds(
                nearby.stream().map(Nearby::lot).toList(), realtime.observedAt());
        List<ParkingLotView> views = new ArrayList<>();

        for (Nearby entry : nearby) {
            RealtimeParkingLot lot = entry.lot();
            double distance = entry.distanceMeters();

            boolean usable = lot.hasRealtime() && !stuck.contains(lot.prkId());
            Integer available = usable ? lot.availableLots() : null;

            views.add(new ParkingLotView(
                    lot.name(),
                    lot.latitude(),
                    lot.longitude(),
                    (int) Math.round(distance),
                    lot.totalLots(),
                    usable ? lot.occupiedLots() : null,
                    available,
                    available == null || lot.totalLots() == null
                            ? null : ParkingCongestion.of(lot.totalLots(), available),
                    available == null ? null : realtime.observedAt(),
                    GnItsClient.SOURCE));
        }

        return views;
    }

    private List<ParkingLotView> nearbyCatalog(Optional<ParkingLotSnapshot> snapshot,
                                               BigDecimal latitude, BigDecimal longitude) {
        if (snapshot.isEmpty()) {
            return List.of();
        }

        BigDecimal latitudeSpan = degreeSpan(RADIUS_METERS, 1);
        BigDecimal longitudeSpan = degreeSpan(RADIUS_METERS,
                Math.cos(Math.toRadians(latitude.doubleValue())));

        List<ParkingLot> candidates = lotRepository.findWithinBox(
                snapshot.get(),
                latitude.subtract(latitudeSpan), latitude.add(latitudeSpan),
                longitude.subtract(longitudeSpan), longitude.add(longitudeSpan));

        List<ParkingLotView> views = new ArrayList<>();

        for (ParkingLot lot : candidates) {
            Double distance = Coordinates.distanceMeters(
                    latitude, longitude, lot.getLatitude(), lot.getLongitude());

            if (distance == null || distance > RADIUS_METERS) {
                continue;
            }

            views.add(new ParkingLotView(
                    lot.getName(),
                    lot.getLatitude(),
                    lot.getLongitude(),
                    (int) Math.round(distance),
                    lot.getCapacity(),
                    null,
                    null,
                    null,
                    null,
                    STANDARD_SOURCE));
        }

        return views;
    }

    /**
     * 같은 주차장이 두 번 나오지 않게 한다.
     *
     * <p>실시간 하나당 가장 가까운 표준데이터 한 곳만 짝지어 지운다. 반경 안의 모든 표준
     * 데이터를 거리로 지우면 가까이 붙은 별개 주차장까지 사라진다.
     */
    private List<ParkingLotView> withoutDuplicates(List<ParkingLotView> realtime,
                                                   List<ParkingLotView> catalog) {
        if (realtime.isEmpty() || catalog.isEmpty()) {
            return catalog;
        }

        List<ParkingLotView> remaining = new ArrayList<>(catalog);

        for (ParkingLotView lot : realtime.stream()
                .sorted(Comparator.comparingInt(ParkingLotView::distanceMeters))
                .toList()) {

            ParkingLotView match = null;
            double best = SAME_LOT_METERS;

            for (ParkingLotView candidate : remaining) {
                Double gap = Coordinates.distanceMeters(
                        lot.latitude(), lot.longitude(),
                        candidate.latitude(), candidate.longitude());

                if (gap != null && gap <= best) {
                    best = gap;
                    match = candidate;
                }
            }

            if (match != null) {
                remaining.remove(match);
            }
        }

        return remaining;
    }

    /**
     * 실시간을 아는 곳을 먼저, 각 묶음 안에서는 가까운 순.
     *
     * <p>거리순으로만 담으면 가까운 표준데이터 주차장 여덟 곳에 밀려 실시간 항목이 목록에서
     * 통째로 빠진다. 지금 자리가 있는지가 이 신호의 목적이라 그쪽을 앞에 둔다. 상태 판정은
     * 잘라내기 전 전체 목록으로 하므로 잘림 때문에 AVAILABLE 이 STATIC_ONLY 로 내려가지 않는다.
     */
    private static List<ParkingLotView> order(List<ParkingLotView> realtime,
                                              List<ParkingLotView> catalog) {
        Comparator<ParkingLotView> byDistance =
                Comparator.comparingInt(ParkingLotView::distanceMeters)
                        .thenComparing(ParkingLotView::name);

        return Stream.concat(
                        realtime.stream().filter(ParkingLotView::hasRealtime).sorted(byDistance),
                        Stream.concat(
                                        realtime.stream().filter(lot -> !lot.hasRealtime()),
                                        catalog.stream())
                                .sorted(byDistance))
                .toList();
    }

    private static String sourcesOf(List<ParkingLotView> lots) {
        Set<String> sources = new LinkedHashSet<>();

        for (ParkingLotView lot : lots) {
            sources.add(lot.source());
        }

        return String.join(" · ", sources);
    }

    /** 주차장이 없다고 말하려면 어느 공급자에게 물어본 결과인지 함께 밝혀야 한다. */
    private static String consultedSources(Realtime realtime,
                                           Optional<ParkingLotSnapshot> snapshot) {
        List<String> sources = new ArrayList<>();

        if (realtime.reachable()) {
            sources.add(GnItsClient.SOURCE);
        }

        if (snapshot.isPresent()) {
            sources.add(STANDARD_SOURCE);
        }

        return sources.isEmpty() ? null : String.join(" · ", sources);
    }

    /** 사각형으로 1차로 좁히기 위한 반경의 도 단위 길이. 정확한 거리는 뒤에서 다시 잰다. */
    private static BigDecimal degreeSpan(int meters, double scale) {
        double span = meters / (METERS_PER_DEGREE * Math.max(scale, 0.1));

        return new BigDecimal(span, MathContext.DECIMAL64);
    }

    /**
     * 실시간 공급자 조회 결과.
     *
     * @param reachable  기본정보를 받았는지. 실패면 실시간 쪽은 아무것도 말할 수 없다
     * @param dataStatus 점유 값의 신선도
     * @param observedAt 점유 값을 받은 시각. 공급자에 기준시각 필드가 없어 수신 시각이다
     */
    private record Realtime(boolean reachable, List<RealtimeParkingLot> lots,
                            DataStatus dataStatus, LocalDateTime observedAt) {

        static Realtime unreachable() {
            return new Realtime(false, List.of(), DataStatus.NO_DATA, null);
        }
    }

    /** 반경 안에 든 실시간 주차장 하나와 그 거리. 거리를 두 번 재지 않으려고 들고 다닌다. */
    private record Nearby(RealtimeParkingLot lot, double distanceMeters) {
    }
}
