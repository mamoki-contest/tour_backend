package com.mamoki.tour.domain.currentaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.currentaccess.dto.CurrentAccessView;
import com.mamoki.tour.domain.currentaccess.dto.ParkingView;
import com.mamoki.tour.domain.currentaccess.dto.RoadFlowView;
import com.mamoki.tour.domain.currentaccess.enums.ParkingStatus;
import com.mamoki.tour.domain.currentaccess.service.CurrentAccessService;
import com.mamoki.tour.domain.currentaccess.service.ParkingAccessService;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.its.ItsClient;
import com.mamoki.tour.infra.its.ItsProperties;

/**
 * 현재 접근 혼잡. 저장한 실제 ITS 응답으로 검증한다.
 *
 * <p>fixture 는 2026-09-18 경포해변 좌표 주변 ±0.005도(약 555m)를 실제 호출해 받은 44구간이다.
 */
class CurrentAccessServiceTest {

    private static final BigDecimal LATITUDE = new BigDecimal("37.8055");
    private static final BigDecimal LONGITUDE = new BigDecimal("128.9078");

    private String fixture;
    private ExternalApiCacheService cacheService;
    private ParkingAccessService parkingAccessService;
    private CurrentAccessService service;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/its-trafficInfo.json")) {
            fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        ItsClient client = Mockito.mock(ItsClient.class);
        given(client.trafficInfoKey(any(), any())).willReturn("trafficInfo?minX=128.9028");
        given(client.parse(anyString()))
                .willAnswer(invocation -> new ItsClient(properties()).parse(invocation.getArgument(0)));

        cacheService = Mockito.mock(ExternalApiCacheService.class);
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available(fixture, LocalDateTime.now()));

        parkingAccessService = Mockito.mock(ParkingAccessService.class);
        given(parkingAccessService.resolve(any(), any())).willReturn(ParkingView.noData());

        service = new CurrentAccessService(client, cacheService, parkingAccessService);
    }

    private ItsProperties properties() {
        return new ItsProperties("http://example.invalid", "key", new BigDecimal("0.005"),
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("주변 도로의 현재 소통을 요약해 돌려준다")
    void summarizesRoadFlow() {
        CurrentAccessView view = service.resolve(LATITUDE, LONGITUDE);

        RoadFlowView road = view.road();
        assertThat(road.status()).isEqualTo(DataStatus.AVAILABLE);
        assertThat(road.linkCount()).isEqualTo(44);
        assertThat(road.averageSpeed()).isPositive();
        assertThat(road.observedAt()).isNotNull();
        assertThat(view.source()).isEqualTo("국가교통정보센터");
        assertThat(view.checkedAt()).isNotNull();
    }

    @Test
    @DisplayName("도로명별로 묶어 관측 구간이 많은 순으로 담는다")
    void groupsByRoadName() {
        RoadFlowView road = service.resolve(LATITUDE, LONGITUDE).road();

        assertThat(road.roads()).isNotEmpty();
        assertThat(road.roads()).hasSizeLessThanOrEqualTo(5);
        assertThat(road.roads()).extracting(RoadFlowView.RoadSegmentView::roadName)
                .doesNotHaveDuplicates()
                .allSatisfy(name -> assertThat(name).isNotBlank())
                .contains("경포로");
        assertThat(road.roads()).isSortedAccordingTo(
                (a, b) -> Integer.compare(b.linkCount(), a.linkCount()));
    }

    @Test
    @DisplayName("속도를 원본 그대로 담고 혼잡 등급으로 바꾸지 않는다")
    void keepsRawSpeed() {
        RoadFlowView road = service.resolve(LATITUDE, LONGITUDE).road();

        assertThat(road.roads()).allSatisfy(segment -> {
            assertThat(segment.averageSpeed()).isPositive();
            assertThat(segment.averageTravelTime()).isNotNegative();
        });
    }

    @Test
    @DisplayName("주차는 별도 공급자에서 온다. 도로가 비어도 주차를 감추지 않는다")
    void delegatesParkingToItsOwnProvider() {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.noData());
        given(parkingAccessService.resolve(any(), any())).willReturn(new ParkingView(
                ParkingStatus.AVAILABLE, DataStatus.AVAILABLE, List.of(),
                LocalDateTime.now(), "강릉시 교통정보 조회서비스"));

        CurrentAccessView view = service.resolve(LATITUDE, LONGITUDE);

        assertThat(view.road().status()).isEqualTo(DataStatus.NO_DATA);
        assertThat(view.parking().status()).isEqualTo(ParkingStatus.AVAILABLE);
        Mockito.verify(parkingAccessService).resolve(LATITUDE, LONGITUDE);
    }

    @Test
    @DisplayName("좌표가 없어도 주차 쪽 판단은 주차 서비스에 맡긴다")
    void delegatesMissingCoordinatesToParkingService() {
        service.resolve(null, LONGITUDE);

        Mockito.verify(parkingAccessService).resolve(null, LONGITUDE);
    }

    @Test
    @DisplayName("좌표가 없으면 다른 동네를 보여주지 않고 정보 없음으로 둔다")
    void skipsWithoutCoordinates() {
        assertThat(service.resolve(null, LONGITUDE).road().status()).isEqualTo(DataStatus.NO_DATA);
        assertThat(service.resolve(LATITUDE, null).road().status()).isEqualTo(DataStatus.NO_DATA);

        Mockito.verifyNoInteractions(cacheService);
    }

    @Test
    @DisplayName("공급자를 부르지 못하면 혼잡을 만들어내지 않고 정보 없음으로 둔다")
    void reportsNoDataWhenProviderUnavailable() {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.noData());

        RoadFlowView road = service.resolve(LATITUDE, LONGITUDE).road();

        assertThat(road.status()).isEqualTo(DataStatus.NO_DATA);
        assertThat(road.averageSpeed()).isNull();
        assertThat(road.linkCount()).isNull();
        assertThat(road.roads()).isEmpty();
    }

    @Test
    @DisplayName("관측 구간이 없으면 평균을 만들지 않는다")
    void reportsNoDataWhenNoLinks() {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available(
                        "{\"header\":{\"resultCode\":0,\"resultMsg\":\"SUCCESS\"},"
                                + "\"body\":{\"totalCount\":0,\"items\":[]}}",
                        LocalDateTime.now()));

        assertThat(service.resolve(LATITUDE, LONGITUDE).road().status())
                .isEqualTo(DataStatus.NO_DATA);
    }

    @Test
    @DisplayName("오류 응답은 예외로 바꾼다. 빈 도로 상태로 위장하지 않는다")
    void rejectsErrorResponse() {
        ItsClient client = new ItsClient(properties());

        assertThatThrownBy(() -> client.parse(
                "{\"header\":{\"resultCode\":1,\"resultMsg\":\"ERROR\"}}"))
                .isInstanceOf(ExternalApiException.class);
    }
}
