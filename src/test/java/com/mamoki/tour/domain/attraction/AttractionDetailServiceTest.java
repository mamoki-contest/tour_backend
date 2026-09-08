package com.mamoki.tour.domain.attraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.attraction.dto.AttractionDetailResponse;
import com.mamoki.tour.domain.attraction.service.AttractionDetailService;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.relatedplace.dto.RelatedPlaces;
import com.mamoki.tour.domain.relatedplace.enums.RelatedPlacesStatus;
import com.mamoki.tour.domain.relatedplace.service.RelatedPlaceService;
import com.mamoki.tour.domain.visittiming.dto.DailyVisitTiming;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;
import com.mamoki.tour.domain.visittiming.dto.VisitTimingDetail;
import com.mamoki.tour.domain.visittiming.enums.DateMode;
import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;
import com.mamoki.tour.domain.visittiming.service.VisitTimingService;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.exception.ServiceException;
import com.mamoki.tour.infra.korservice.KorServiceClient;
import com.mamoki.tour.infra.korservice.KorServiceProperties;

/**
 * 관광지 상세 조립.
 *
 * <p>기본정보·예측·연관 장소는 서로 다른 공급자에서 오고 갱신 주기도 다르다. 하나가 비어도
 * 나머지를 채우되, 기본정보만은 없으면 무엇에 대한 상세인지 말할 수 없어 404 로 응답한다.
 */
class AttractionDetailServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);
    private static final String GANGNEUNG = "51150";

    private AttractionDetailService detailService;
    private ExternalApiCacheService cacheService;
    private RegionCodeRepository regionCodeRepository;
    private VisitTimingService visitTimingService;
    private RelatedPlaceService relatedPlaceService;
    private KorServiceClient client;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(KorServiceClient.class);
        given(client.detailCommonKey(anyString()))
                .willAnswer(call -> "detailCommon2?contentId=" + call.getArgument(0));
        given(client.parse(anyString(), anyString()))
                .willAnswer(call -> realClient().parse(call.getArgument(0), call.getArgument(1)));

        cacheService = Mockito.mock(ExternalApiCacheService.class);
        regionCodeRepository = Mockito.mock(RegionCodeRepository.class);
        given(regionCodeRepository.findByLawdCode(anyString())).willReturn(Optional.empty());

        visitTimingService = Mockito.mock(VisitTimingService.class);
        given(visitTimingService.resolveDetail(any(), any())).willReturn(visitTimingDetail());

        relatedPlaceService = Mockito.mock(RelatedPlaceService.class);
        given(relatedPlaceService.resolve(any(), any()))
                .willReturn(RelatedPlaces.noData("202607"));

        detailService = new AttractionDetailService(
                client, cacheService, regionCodeRepository, visitTimingService, relatedPlaceService);
    }

    private KorServiceClient realClient() {
        return new KorServiceClient(new KorServiceProperties(
                "http://example.invalid", "key", "tour",
                java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("기본정보와 30일 예측을 함께 담는다")
    void includesBasicInfoAndForecast() {
        givenDetail(body());

        AttractionDetailResponse detail = detailService.getDetail("126508", TODAY);

        assertThat(detail.contentId()).isEqualTo("126508");
        assertThat(detail.name()).isEqualTo("경포해변");
        assertThat(detail.address()).isEqualTo("강원특별자치도 강릉시 창해로 514");
        assertThat(detail.lawdCode()).isEqualTo(GANGNEUNG);
        assertThat(detail.visitTiming().status()).isEqualTo(VisitTimingStatus.LOW);
        assertThat(detail.dailyForecast()).hasSize(30);
    }

    @Test
    @DisplayName("상세 전용 필드를 함께 내려준다")
    void includesDetailOnlyFields() {
        givenDetail(body());

        AttractionDetailResponse detail = detailService.getDetail("126508", TODAY);

        assertThat(detail.tel()).isEqualTo("033-640-4901");
        assertThat(detail.homepage()).isEqualTo("https://www.gn.go.kr");
        assertThat(detail.overview()).startsWith("경포해변은");
        assertThat(detail.zipcode()).isEqualTo("25460");
    }

    @Test
    @DisplayName("공급자가 빈 문자열로 준 값은 0 이나 빈 문자열이 아니라 null 로 남긴다")
    void keepsBlankValuesNull() {
        givenDetail(bodyWithBlanks());

        AttractionDetailResponse detail = detailService.getDetail("126508", TODAY);

        assertThat(detail.tel()).isNull();
        assertThat(detail.homepage()).isNull();
        assertThat(detail.overview()).isNull();
        assertThat(detail.latitude()).isNull();
    }

    @Test
    @DisplayName("기본정보를 얻지 못하면 404 로 응답하고 예측·연관 장소를 조회하지 않는다")
    void failsWithNotFoundWhenBasicInfoMissing() {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.noData());

        assertThatThrownBy(() -> detailService.getDetail("999", TODAY))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("찾을 수 없습니다");

        verify(visitTimingService, never()).resolveDetail(any(), any());
        verify(relatedPlaceService, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("공급자가 항목을 주지 않으면 빈 상세로 위장하지 않고 404 로 응답한다")
    void failsWithNotFoundWhenItemsEmpty() {
        givenDetail("""
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},\
                "body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}""");

        assertThatThrownBy(() -> detailService.getDetail("999", TODAY))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("연관 장소를 얻지 못해도 상세는 200 으로 응답하고 상태로 알린다")
    void stillReturnsDetailWhenRelatedPlacesMissing() {
        givenDetail(body());

        AttractionDetailResponse detail = detailService.getDetail("126508", TODAY);

        assertThat(detail.alternatives().status()).isEqualTo(RelatedPlacesStatus.NO_RELATED_DATA);
        assertThat(detail.companions().status()).isEqualTo(RelatedPlacesStatus.NO_RELATED_DATA);
        assertThat(detail.alternatives().items()).isEmpty();
    }

    @Test
    @DisplayName("최종 정상 데이터로 응답하면 상태와 기준 시점을 함께 알린다")
    void reportsStaleWithCollectedAt() {
        LocalDateTime collectedAt = LocalDateTime.of(2026, 9, 1, 3, 0);
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.stale(body(), collectedAt));

        AttractionDetailResponse detail = detailService.getDetail("126508", TODAY);

        assertThat(detail.dataStatus()).isEqualTo(DataStatus.STALE);
        assertThat(detail.collectedAt()).isEqualTo(collectedAt);
        assertThat(detail.source()).isEqualTo("KorService2");
    }

    @Test
    @DisplayName("지역코드 매핑이 없으면 지역명을 만들어내지 않는다")
    void keepsRegionNameNullWhenUnmapped() {
        givenDetail(body());

        assertThat(detailService.getDetail("126508", TODAY).regionName()).isNull();
    }

    @Test
    @DisplayName("지역코드 매핑이 있으면 시·군 이름을 채운다")
    void fillsRegionNameWhenMapped() {
        givenDetail(body());
        given(regionCodeRepository.findByLawdCode(GANGNEUNG))
                .willReturn(Optional.of(RegionCode.builder()
                        .lawdCode(GANGNEUNG).areaCode("32").name("강릉시").build()));

        assertThat(detailService.getDetail("126508", TODAY).regionName()).isEqualTo("강릉시");
    }

    private void givenDetail(String body) {
        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available(body, LocalDateTime.now()));
    }

    private static VisitTimingDetail visitTimingDetail() {
        List<DailyVisitTiming> daily = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> new DailyVisitTiming(TODAY.plusDays(i), VisitTimingStatus.NORMAL))
                .toList();

        VisitTiming summary = new VisitTiming(DateMode.FLEXIBLE, VisitTimingStatus.LOW, null,
                TODAY.plusDays(3), 30, TODAY, TODAY.plusDays(29), DataStatus.AVAILABLE,
                LocalDateTime.of(2026, 9, 8, 3, 0), "TatsCnctrRateService");

        return new VisitTimingDetail(summary, daily);
    }

    /** detailCommon2 실제 응답 구조를 그대로 흉내 낸 JSON. */
    private static String body() {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[\
                {"contentid":"126508","contenttypeid":"12","title":"경포해변",\
                "createdtime":"20031230090000","modifiedtime":"20260520091252",\
                "tel":"033-640-4901","homepage":"https://www.gn.go.kr",\
                "firstimage":"https://tong.visitkorea.or.kr/image.jpg","firstimage2":"",\
                "areacode":"","sigungucode":"","lDongRegnCd":"51","lDongSignguCd":"150",\
                "cat1":"","addr1":"강원특별자치도 강릉시 창해로 514","addr2":"","zipcode":"25460",\
                "mapx":"128.9017861","mapy":"37.8049458",\
                "overview":"경포해변은 강릉을 대표하는 해수욕장이다."}\
                ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}""";
    }

    /** 공급자는 값이 없을 때 null 이 아니라 빈 문자열을 준다. */
    private static String bodyWithBlanks() {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":[\
                {"contentid":"126508","contenttypeid":"12","title":"경포해변",\
                "modifiedtime":"","tel":"","homepage":"","firstimage":"",\
                "lDongRegnCd":"51","lDongSignguCd":"150","addr1":"","addr2":"","zipcode":"",\
                "mapx":"","mapy":"","overview":""}\
                ]},"numOfRows":1,"pageNo":1,"totalCount":1}}}""";
    }
}
