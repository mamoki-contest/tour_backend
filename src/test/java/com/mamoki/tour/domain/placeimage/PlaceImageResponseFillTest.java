package com.mamoki.tour.domain.placeimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;

import com.mamoki.tour.domain.attraction.dto.AttractionDetailResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.attraction.repository.AttractionCatalogImportRepository;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.attraction.service.AttractionDetailService;
import com.mamoki.tour.domain.attraction.service.AttractionService;
import com.mamoki.tour.domain.attraction.service.CenterRankService;
import com.mamoki.tour.domain.attraction.service.SignalLookupService;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.currentaccess.dto.CurrentAccessView;
import com.mamoki.tour.domain.currentaccess.dto.ParkingView;
import com.mamoki.tour.domain.currentaccess.dto.RoadFlowView;
import com.mamoki.tour.domain.currentaccess.service.CurrentAccessService;
import com.mamoki.tour.domain.placeimage.dto.PlaceImageView;
import com.mamoki.tour.domain.placeimage.service.PlaceImageLookupService;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.relatedplace.dto.RelatedPlaces;
import com.mamoki.tour.domain.relatedplace.service.RelatedPlaceService;
import com.mamoki.tour.domain.visittiming.dto.DailyVisitTiming;
import com.mamoki.tour.domain.visittiming.dto.VisitTiming;
import com.mamoki.tour.domain.visittiming.dto.VisitTimingDetail;
import com.mamoki.tour.domain.visittiming.enums.DateMode;
import com.mamoki.tour.domain.visittiming.enums.VisitTimingStatus;
import com.mamoki.tour.domain.visittiming.service.VisitTimingService;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.enums.ImageSource;
import com.mamoki.tour.infra.korservice.KorServiceClient;
import com.mamoki.tour.infra.korservice.KorServiceProperties;

/**
 * 찾아 둔 사진이 조회 응답에 어떻게 실리는가(#99).
 *
 * <p>규칙은 하나다 — <b>공급자 사진이 있으면 그대로 둔다.</b> 우리가 찾은 제3자 사진은
 * 빈 자리를 메우는 것이지 공급자 사진을 대신하는 것이 아니다. 이 규칙이 깨지면 화면만
 * 봐서는 아무도 알아채지 못한 채 출처가 조용히 바뀐다.
 *
 * <p>목록은 썸네일을, 상세는 원본을 쓴다. 카드 수십 장에 원본을 걸면 목록이 느려진다.
 */
class PlaceImageResponseFillTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);
    private static final String GANGNEUNG = "51150";

    private static final String THUMBNAIL = "https://search.pstatic.net/thumb.jpg";
    private static final String ORIGINAL = "https://blogfiles.pstatic.net/original.jpg";
    private static final String SOURCE_PAGE =
            "https://search.naver.com/search.naver?where=image&query=%EA%B2%BD%ED%8F%AC";
    private static final String PROVIDER_IMAGE = "https://tong.visitkorea.or.kr/a.jpg";

    private PlaceImageLookupService placeImageLookupService;
    private AttractionService attractionService;
    private AttractionDetailService detailService;
    private ExternalApiCacheService cacheService;
    private KorServiceClient korServiceClient;

    @BeforeEach
    void setUp() {
        placeImageLookupService = Mockito.mock(PlaceImageLookupService.class);
        given(placeImageLookupService.findUsable(any(Collection.class))).willReturn(Map.of());
        given(placeImageLookupService.findUsable(anyString())).willReturn(Optional.empty());

        korServiceClient = Mockito.mock(KorServiceClient.class);
        given(korServiceClient.detailCommonKey(anyString()))
                .willAnswer(call -> "detailCommon2?contentId=" + call.getArgument(0));
        given(korServiceClient.parse(anyString(), anyString()))
                .willAnswer(call -> realClient().parse(call.getArgument(0), call.getArgument(1)));

        cacheService = Mockito.mock(ExternalApiCacheService.class);

        RegionCodeRepository regionCodeRepository = Mockito.mock(RegionCodeRepository.class);
        given(regionCodeRepository.findAllByAreaCode(anyString())).willReturn(List.of());
        given(regionCodeRepository.findByLawdCode(anyString())).willReturn(Optional.empty());

        SignalLookupService signalLookupService = Mockito.mock(SignalLookupService.class);
        given(signalLookupService.findOnlineMentions(any())).willReturn(Optional.empty());
        given(signalLookupService.findTmapRanks(any())).willReturn(Map.of());
        given(signalLookupService.findVisitorStats(any())).willReturn(Optional.empty());
        given(signalLookupService.findMentionRuleVersion()).willReturn(Optional.empty());

        VisitTimingService visitTimingService = Mockito.mock(VisitTimingService.class);
        given(visitTimingService.resolve(any(), any(), any())).willReturn(Map.of());
        given(visitTimingService.resolveDetail(any(), any())).willReturn(visitTimingDetail());

        RelatedPlaceService relatedPlaceService = Mockito.mock(RelatedPlaceService.class);
        given(relatedPlaceService.resolve(any(), any())).willReturn(RelatedPlaces.noData("202607"));

        CurrentAccessService currentAccessService = Mockito.mock(CurrentAccessService.class);
        given(currentAccessService.resolve(any(), any())).willReturn(new CurrentAccessView(
                RoadFlowView.noData(), ParkingView.noData(), LocalDateTime.now(),
                "국가교통정보센터"));

        attractionService = new AttractionService(korServiceClient, cacheService,
                regionCodeRepository, Mockito.mock(CenterRankService.class), signalLookupService,
                visitTimingService, Mockito.mock(AttractionRepository.class),
                Mockito.mock(AttractionCatalogImportRepository.class), placeImageLookupService);

        detailService = new AttractionDetailService(korServiceClient, cacheService,
                regionCodeRepository, visitTimingService, relatedPlaceService,
                currentAccessService, placeImageLookupService);
    }

    private static KorServiceClient realClient() {
        return new KorServiceClient(new KorServiceProperties("http://example.invalid", "key",
                "tour", Duration.ofSeconds(1), Duration.ofSeconds(1)), RestClient.builder());
    }

    private static VisitTimingDetail visitTimingDetail() {
        List<DailyVisitTiming> daily = List.of(
                new DailyVisitTiming(TODAY, VisitTimingStatus.NORMAL));

        VisitTiming summary = new VisitTiming(DateMode.FLEXIBLE, VisitTimingStatus.LOW, null,
                TODAY, 1, TODAY, TODAY, DataStatus.AVAILABLE,
                LocalDateTime.of(2026, 9, 8, 3, 0), "TatsCnctrRateService");

        return new VisitTimingDetail(summary, daily);
    }

    private static AttractionSnapshot snapshot(String contentId, String imageUrl) {
        return new AttractionSnapshot(contentId, "경포해변", imageUrl, "강원특별자치도 강릉시",
                null, null, "12", GANGNEUNG, LocalDateTime.of(2026, 9, 1, 0, 0), "KorService2");
    }

    private void givenFoundImage(String contentId) {
        PlaceImageView view = new PlaceImageView(THUMBNAIL, ORIGINAL, SOURCE_PAGE,
                ImageSource.NAVER_IMAGE);

        given(placeImageLookupService.findUsable(any(Collection.class)))
                .willReturn(Map.of(contentId, view));
        given(placeImageLookupService.findUsable(contentId)).willReturn(Optional.of(view));
    }

    /** 공급자 상세 응답을 흉내 낸다. {@code firstimage} 가 빈 관광지가 채움의 대상이다. */
    private void givenProviderDetail(String imageUrl) {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},\
                "body":{"items":{"item":[{"contentid":"1","title":"경포해변",\
                "firstimage":"%s","addr1":"강원특별자치도 강릉시","lDongRegnCd":"51",\
                "lDongSigunguCd":"150","contenttypeid":"12"}]},\
                "numOfRows":1,"pageNo":1,"totalCount":1}}}"""
                .formatted(imageUrl == null ? "" : imageUrl);

        given(cacheService.fetch(any(), anyString(), any(), any()))
                .willReturn(CachedResponse.available(body, LocalDateTime.now()));
    }

    @Test
    @DisplayName("목록: 사진이 없으면 찾아 둔 썸네일로 채우고 출처를 함께 내린다")
    void fillsListImageFromPlaceImage() {
        givenFoundImage("1");

        AttractionResponse item = attractionService
                .describe(List.of(snapshot("1", null)), null, null, null).get(0);

        assertThat(item.imageUrl()).isEqualTo(THUMBNAIL);
        assertThat(item.imageSource()).isEqualTo(ImageSource.NAVER_IMAGE);
        assertThat(item.imageSourceUrl()).isEqualTo(SOURCE_PAGE);
    }

    @Test
    @DisplayName("목록: 공급자 사진이 있으면 그대로 둔다")
    void keepsProviderImageInList() {
        givenFoundImage("1");

        AttractionResponse item = attractionService
                .describe(List.of(snapshot("1", PROVIDER_IMAGE)), null, null, null).get(0);

        assertThat(item.imageUrl()).isEqualTo(PROVIDER_IMAGE);
        assertThat(item.imageSource()).isEqualTo(ImageSource.KOR_SERVICE);
        assertThat(item.imageSourceUrl()).isNull();
    }

    @Test
    @DisplayName("목록: 빈 문자열도 사진 없음으로 본다")
    void treatsBlankProviderImageAsMissing() {
        givenFoundImage("1");

        AttractionResponse item = attractionService
                .describe(List.of(snapshot("1", "")), null, null, null).get(0);

        assertThat(item.imageUrl()).isEqualTo(THUMBNAIL);
        assertThat(item.imageSource()).isEqualTo(ImageSource.NAVER_IMAGE);
    }

    @Test
    @DisplayName("목록: 채울 자리가 하나도 없으면 표를 읽지 않는다")
    void doesNotQueryWhenEveryItemHasAnImage() {
        attractionService.describe(List.of(snapshot("1", PROVIDER_IMAGE)), null, null, null);

        verify(placeImageLookupService, never()).findUsable(any(Collection.class));
    }

    @Test
    @DisplayName("목록: 찾아 둔 사진이 없으면 사진 없음 그대로다 - 값을 만들어내지 않는다")
    void leavesMissingImageAloneWhenNothingWasFound() {
        AttractionResponse item = attractionService
                .describe(List.of(snapshot("1", null)), null, null, null).get(0);

        assertThat(item.imageUrl()).isNull();
        assertThat(item.imageSource()).isNull();
        assertThat(item.imageSourceUrl()).isNull();
    }

    @Test
    @DisplayName("상세: 사진이 없으면 찾아 둔 원본으로 채운다 - 썸네일이 아니다")
    void fillsDetailImageWithTheOriginal() {
        givenFoundImage("1");
        givenProviderDetail(null);

        AttractionDetailResponse detail = detailService.getDetail("1", TODAY);

        assertThat(detail.imageUrl()).isEqualTo(ORIGINAL);
        assertThat(detail.imageSource()).isEqualTo(ImageSource.NAVER_IMAGE);
        assertThat(detail.imageSourceUrl()).isEqualTo(SOURCE_PAGE);
    }

    @Test
    @DisplayName("상세: 공급자 사진이 있으면 그대로 둔다")
    void keepsProviderImageInDetail() {
        givenFoundImage("1");
        givenProviderDetail(PROVIDER_IMAGE);

        AttractionDetailResponse detail = detailService.getDetail("1", TODAY);

        assertThat(detail.imageUrl()).isEqualTo(PROVIDER_IMAGE);
        assertThat(detail.imageSource()).isEqualTo(ImageSource.KOR_SERVICE);
        assertThat(detail.imageSourceUrl()).isNull();
    }

    @Test
    @DisplayName("상세: 찾아 둔 사진이 없으면 사진 없음 그대로다")
    void leavesDetailImageAloneWhenNothingWasFound() {
        givenProviderDetail(null);

        AttractionDetailResponse detail = detailService.getDetail("1", TODAY);

        assertThat(detail.imageUrl()).isNull();
        assertThat(detail.imageSource()).isNull();
        assertThat(detail.imageSourceUrl()).isNull();
    }
}
