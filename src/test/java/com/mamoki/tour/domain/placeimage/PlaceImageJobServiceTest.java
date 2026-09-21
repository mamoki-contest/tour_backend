package com.mamoki.tour.domain.placeimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.mention.support.SearchQueryRule;
import com.mamoki.tour.domain.placeimage.PlaceImageProperties;
import com.mamoki.tour.domain.placeimage.entity.PlaceImage;
import com.mamoki.tour.domain.placeimage.enums.PlaceImageStatus;
import com.mamoki.tour.domain.placeimage.importer.PlaceImageJobRequest;
import com.mamoki.tour.domain.placeimage.importer.PlaceImageJobService;
import com.mamoki.tour.domain.placeimage.importer.PlaceImageResult;
import com.mamoki.tour.domain.placeimage.importer.PlaceImageWriter;
import com.mamoki.tour.domain.placeimage.repository.PlaceImageRepository;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.enums.ImageSource;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.naver.NaverApiHubProperties;
import com.mamoki.tour.infra.naver.NaverAuthenticationException;
import com.mamoki.tour.infra.naver.NaverImageSearchClient;
import com.mamoki.tour.infra.naver.NaverRateLimitException;
import com.mamoki.tour.infra.naver.dto.NaverImageSearchResponse;

/**
 * 사진 없는 관광지의 대표 사진을 네이버 이미지 검색으로 채우는 배치.
 *
 * <p>네이버 호출은 흉내 낸다. 여기서 보는 것은 <b>배치가 한도와 재실행을 어떻게 다루는가</b>다.
 * 조용히 틀리는 쪽이 셋이다 — 이미 채운 관광지를 다시 불러 하루 한도를 깎는 것, 인증이
 * 막혔는데 남은 수천 곳을 계속 부르는 것, 그리고 공급자 사진이 있는 곳까지 대상으로 삼는 것.
 */
@SpringBootTest
@ActiveProfiles("test")
class PlaceImageJobServiceTest {

    private static final String GANGNEUNG = "51150";
    private static final String SOKCHO = "51210";

    private static final String THUMBNAIL =
            "https://search.pstatic.net/common/?src=https%3A%2F%2Fa%2Fb.jpg&type=b150";
    private static final String IMAGE = "https://blogfiles.pstatic.net/a/b.jpg";

    @MockitoBean
    private NaverImageSearchClient imageSearchClient;

    @Autowired
    private PlaceImageJobService jobService;

    @Autowired
    private PlaceImageWriter writer;

    @Autowired
    private PlaceImageRepository placeImageRepository;

    @Autowired
    private AttractionRepository attractionRepository;

    @Autowired
    private RegionCodeRepository regionCodeRepository;

    @Autowired
    private SearchQueryRule queryRule;

    @BeforeEach
    void reset() {
        placeImageRepository.deleteAllInBatch();
        attractionRepository.deleteAllInBatch();

        givenNaverFinds(IMAGE, THUMBNAIL);
    }

    private void givenNaverFinds(String link, String thumbnail) {
        given(imageSearchClient.search(anyString(), anyInt(), anyString()))
                .willReturn(new NaverImageSearchResponse("now", 1L, 1, 3,
                        List.of(new NaverImageSearchResponse.Item("제목", link, thumbnail,
                                "800", "1200"))));
    }

    private void givenNaverFindsNothing() {
        given(imageSearchClient.search(anyString(), anyInt(), anyString()))
                .willReturn(new NaverImageSearchResponse("now", 0L, 1, 3, List.of()));
    }

    private Attraction saveAttraction(String contentId, String name, String lawdCode,
                                      String imageUrl) {
        RegionCode region = regionCodeRepository.findByLawdCode(lawdCode).orElseThrow();

        return attractionRepository.save(Attraction.builder()
                .contentId(contentId)
                .name(name)
                .imageUrl(imageUrl)
                .regionCode(region)
                .dataStatus(DataStatus.AVAILABLE)
                .source("KorService2")
                .build());
    }

    /** 한도나 키를 바꿔 보려면 설정을 갈아 끼운 인스턴스가 필요하다. */
    private PlaceImageJobService jobServiceWith(NaverApiHubProperties naver,
                                                PlaceImageProperties properties) {
        return new PlaceImageJobService(imageSearchClient, writer, placeImageRepository,
                attractionRepository, queryRule, naver, properties);
    }

    private static NaverApiHubProperties naverWithKey() {
        return new NaverApiHubProperties("https://naverapihub.apigw.ntruss.com", "id", "key",
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ZERO);
    }

    @Test
    @DisplayName("사진이 없는 관광지만 대상으로 삼는다")
    void looksOnlyAtAttractionsWithoutImage() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);
        saveAttraction("2", "빈 문자열", GANGNEUNG, "");
        saveAttraction("3", "오죽헌", GANGNEUNG, "https://tong.visitkorea.or.kr/a.jpg");

        PlaceImageResult result = jobService.run(PlaceImageJobRequest.of(false, null));

        assertThat(result.targets()).isEqualTo(2);
        assertThat(result.calls()).isEqualTo(2);
        assertThat(placeImageRepository.findByContentId("3")).isEmpty();
    }

    @Test
    @DisplayName("검색어는 언급량 수집과 같은 규칙 - 관광지명 + 시·군명")
    void usesTheSameSearchQueryRuleAsMentionCollect() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);

        jobService.run(PlaceImageJobRequest.of(false, null));

        verify(imageSearchClient).search(eq("경포해변 강릉시"), anyInt(), anyString());
    }

    @Test
    @DisplayName("찾은 사진을 표에 남긴다 - 썸네일·원본·출처·공급자")
    void recordsFoundImage() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);

        PlaceImageResult result = jobService.run(PlaceImageJobRequest.of(false, null));

        PlaceImage saved = placeImageRepository.findByContentId("1").orElseThrow();

        assertThat(saved.getStatus()).isEqualTo(PlaceImageStatus.AVAILABLE);
        assertThat(saved.getThumbnailUrl()).isEqualTo(THUMBNAIL);
        assertThat(saved.getImageUrl()).isEqualTo(IMAGE);
        assertThat(saved.getProvider()).isEqualTo(ImageSource.NAVER_IMAGE);
        assertThat(saved.getSourceUrl()).contains("search.naver.com");
        assertThat(saved.getFetchedAt()).isNotNull();
        assertThat(result.filled()).isEqualTo(1);
    }

    @Test
    @DisplayName("결과가 0건이면 NONE 으로 남긴다 - 다음 실행이 또 묻지 않게")
    void recordsNoneWhenProviderHasNothing() {
        saveAttraction("1", "흐르는돌비늘폭포", GANGNEUNG, null);
        givenNaverFindsNothing();

        PlaceImageResult result = jobService.run(PlaceImageJobRequest.of(false, null));

        assertThat(placeImageRepository.findByContentId("1").orElseThrow().getStatus())
                .isEqualTo(PlaceImageStatus.NONE);
        assertThat(result.none()).isEqualTo(1);
        assertThat(result.filled()).isZero();
    }

    @Test
    @DisplayName("다시 돌려도 이미 있는 행은 부르지 않는다")
    void isIdempotent() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);
        jobService.run(PlaceImageJobRequest.of(false, null));

        PlaceImageResult second = jobService.run(PlaceImageJobRequest.of(false, null));

        assertThat(second.skipped()).isEqualTo(1);
        assertThat(second.calls()).isZero();
        assertThat(placeImageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("NONE 으로 남은 행도 다시 부르지 않는다")
    void doesNotRetryNoneRows() {
        saveAttraction("1", "흐르는돌비늘폭포", GANGNEUNG, null);
        givenNaverFindsNothing();
        jobService.run(PlaceImageJobRequest.of(false, null));

        assertThat(jobService.run(PlaceImageJobRequest.of(false, null)).calls()).isZero();
    }

    @Test
    @DisplayName("--refresh 는 있는 행도 다시 수집한다")
    void refreshRecollects() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);
        jobService.run(PlaceImageJobRequest.of(false, null));

        givenNaverFinds("https://blogfiles.pstatic.net/new.jpg", "https://search.pstatic.net/new");
        PlaceImageResult refreshed = jobService.run(PlaceImageJobRequest.of(true, null));

        assertThat(refreshed.calls()).isEqualTo(1);
        assertThat(refreshed.skipped()).isZero();
        assertThat(placeImageRepository.findByContentId("1").orElseThrow().getImageUrl())
                .isEqualTo("https://blogfiles.pstatic.net/new.jpg");
        assertThat(placeImageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("시·군을 지정하면 그 시·군만 본다")
    void narrowsToOneDistrict() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);
        saveAttraction("2", "영금정", SOKCHO, null);

        PlaceImageResult result = jobService.run(PlaceImageJobRequest.of(false, GANGNEUNG));

        assertThat(result.targets()).isEqualTo(1);
        assertThat(placeImageRepository.findByContentId("2")).isEmpty();
    }

    @Test
    @DisplayName("한 실행의 호출 상한에 닿으면 멈추고 이유를 남긴다")
    void stopsAtTheCallCap() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);
        saveAttraction("2", "오죽헌", GANGNEUNG, null);

        PlaceImageResult result = jobServiceWith(naverWithKey(), new PlaceImageProperties(1, 3, "medium"))
                .run(PlaceImageJobRequest.of(false, null));

        assertThat(result.calls()).isEqualTo(1);
        assertThat(result.stoppedEarly()).isTrue();
        assertThat(result.stoppedReason()).contains("상한");
        assertThat(placeImageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("인증이 막히면 즉시 멈춘다 - 남은 관광지를 부르지 않는다")
    void stopsImmediatelyOnAuthenticationFailure() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);
        saveAttraction("2", "오죽헌", GANGNEUNG, null);

        willThrow(new NaverAuthenticationException(ApiProvider.NAVER_IMAGE_SEARCH, "막힘"))
                .given(imageSearchClient).search(anyString(), anyInt(), anyString());

        PlaceImageResult result = jobService.run(PlaceImageJobRequest.of(false, null));

        assertThat(result.calls()).isEqualTo(1);
        assertThat(result.stoppedEarly()).isTrue();
        assertThat(placeImageRepository.count()).isZero();
    }

    @Test
    @DisplayName("한도를 넘으면 즉시 멈춘다")
    void stopsImmediatelyOnRateLimit() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);
        saveAttraction("2", "오죽헌", GANGNEUNG, null);

        willThrow(new NaverRateLimitException(ApiProvider.NAVER_IMAGE_SEARCH, "한도"))
                .given(imageSearchClient).search(anyString(), anyInt(), anyString());

        PlaceImageResult result = jobService.run(PlaceImageJobRequest.of(false, null));

        assertThat(result.calls()).isEqualTo(1);
        assertThat(result.stoppedEarly()).isTrue();
    }

    @Test
    @DisplayName("한 건의 호출 실패는 FAILED 로 남기고 다음으로 간다")
    void recordsFailureAndContinues() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);
        saveAttraction("2", "오죽헌", GANGNEUNG, null);

        given(imageSearchClient.search(eq("경포해변 강릉시"), anyInt(), anyString()))
                .willThrow(new ExternalApiException(ApiProvider.NAVER_IMAGE_SEARCH, "일시 오류"));

        PlaceImageResult result = jobService.run(PlaceImageJobRequest.of(false, null));

        assertThat(result.stoppedEarly()).isFalse();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.filled()).isEqualTo(1);
        assertThat(placeImageRepository.findByContentId("1").orElseThrow().getStatus())
                .isEqualTo(PlaceImageStatus.FAILED);
    }

    @Test
    @DisplayName("키가 없으면 한 번도 부르지 않고 이유를 남긴다")
    void doesNotStartWithoutCredentials() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);

        NaverApiHubProperties noKey = new NaverApiHubProperties(
                "https://naverapihub.apigw.ntruss.com", null, null,
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ZERO);

        PlaceImageResult result = jobServiceWith(noKey, new PlaceImageProperties(0, 0, null))
                .run(PlaceImageJobRequest.of(false, null));

        assertThat(result.stoppedEarly()).isTrue();
        assertThat(result.calls()).isZero();
        verifyNoInteractions(imageSearchClient);
    }

    @Test
    @DisplayName("설정한 표시 수·필터로 부른다")
    void passesDisplayAndFilterFromProperties() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);

        jobServiceWith(naverWithKey(), new PlaceImageProperties(10, 5, "large"))
                .run(PlaceImageJobRequest.of(false, null));

        verify(imageSearchClient).search(anyString(), eq(5), eq("large"));
    }

    @Test
    @DisplayName("대상이 한 곳도 없으면 부르지 않는다")
    void doesNothingWhenEveryAttractionHasAnImage() {
        saveAttraction("1", "오죽헌", GANGNEUNG, "https://tong.visitkorea.or.kr/a.jpg");

        PlaceImageResult result = jobService.run(PlaceImageJobRequest.of(false, null));

        assertThat(result.targets()).isZero();
        verify(imageSearchClient, never()).search(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("멈춘 뒤에도 그때까지 채운 것은 남는다")
    void keepsWhatItFilledBeforeStopping() {
        saveAttraction("1", "경포해변", GANGNEUNG, null);
        saveAttraction("2", "오죽헌", GANGNEUNG, null);

        given(imageSearchClient.search(eq("오죽헌 강릉시"), anyInt(), anyString()))
                .willThrow(new NaverRateLimitException(ApiProvider.NAVER_IMAGE_SEARCH, "한도"));

        jobService.run(PlaceImageJobRequest.of(false, null));

        Optional<PlaceImage> first = placeImageRepository.findByContentId("1");

        assertThat(first).isPresent();
        assertThat(first.get().getStatus()).isEqualTo(PlaceImageStatus.AVAILABLE);
    }
}
