package com.mamoki.tour.domain.placeimage.importer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.mention.support.SearchQueryRule;
import com.mamoki.tour.domain.placeimage.PlaceImageProperties;
import com.mamoki.tour.domain.placeimage.entity.PlaceImage;
import com.mamoki.tour.domain.placeimage.repository.PlaceImageRepository;
import com.mamoki.tour.global.enums.ImageSource;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.naver.NaverApiHubProperties;
import com.mamoki.tour.infra.naver.NaverAuthenticationException;
import com.mamoki.tour.infra.naver.NaverImageSearchClient;
import com.mamoki.tour.infra.naver.NaverRateLimitException;
import com.mamoki.tour.infra.naver.dto.NaverImageSearchResponse;

/**
 * 공급자 사진이 없는 관광지를 돌며 네이버 이미지 검색으로 대표 사진을 찾는다(#99).
 *
 * <p>대상은 카탈로그에서 {@code image_url} 이 비어 있는 관광지다. 공급자(KorService2)가
 * 사진을 준 곳은 건드리지 않는다 — 우리가 찾은 제3자 사진보다 공급자 사진이 언제나 낫다.
 *
 * <p><b>한 번 물어본 관광지는 다시 묻지 않는다.</b> 결과가 없었던 곳(NONE)도 마찬가지다.
 * 다시 물어도 같은 답을 받으면서 하루 한도(25,000회, 블로그 검색과 공유)만 깎는다.
 * 검색어 규칙이나 필터를 바꿔 다른 답을 기대할 때만 {@code --refresh} 로 다시 돈다.
 *
 * <p>멈춰야 할 때 멈춘다. 인증 실패(키가 잘못됐거나 허브에서 이미지 검색이 꺼져 있음)와
 * 한도 초과는 남은 관광지를 계속 불러도 모두 같은 답을 받는 상황이라 그 자리에서 끝낸다.
 * 그때까지 채운 사진은 관광지마다 따로 커밋되어 남는다.
 */
@Service
public class PlaceImageJobService {

    private static final Logger log = LoggerFactory.getLogger(PlaceImageJobService.class);

    private final NaverImageSearchClient imageSearchClient;
    private final PlaceImageWriter writer;
    private final PlaceImageRepository placeImageRepository;
    private final AttractionRepository attractionRepository;
    private final SearchQueryRule queryRule;
    private final NaverApiHubProperties naverProperties;
    private final PlaceImageProperties properties;

    public PlaceImageJobService(NaverImageSearchClient imageSearchClient,
                                PlaceImageWriter writer,
                                PlaceImageRepository placeImageRepository,
                                AttractionRepository attractionRepository,
                                SearchQueryRule queryRule,
                                NaverApiHubProperties naverProperties,
                                PlaceImageProperties properties) {
        this.imageSearchClient = imageSearchClient;
        this.writer = writer;
        this.placeImageRepository = placeImageRepository;
        this.attractionRepository = attractionRepository;
        this.queryRule = queryRule;
        this.naverProperties = naverProperties;
        this.properties = properties;
    }

    public PlaceImageResult run(PlaceImageJobRequest request) {
        if (!naverProperties.hasCredentials()) {
            // 키 없이 돌면 모든 호출이 401 이다. 한 번도 부르지 않고 이유를 남기고 끝낸다.
            log.error("NAVER_API_HUB_KEY_ID/KEY 가 비어 있어 대표 사진 보강을 시작하지 않습니다.");

            return PlaceImageResult.notRun("NAVER API HUB 키 없음");
        }

        List<Attraction> targets = request.hasDistrict()
                ? attractionRepository.findAllWithoutImageByLawdCode(request.lawdCode())
                : attractionRepository.findAllWithoutImage();

        log.info("공급자 사진이 없는 관광지 {}건을 봅니다. 시·군={}, 재수집={}, 호출 상한={}, "
                        + "표시 수={}, 필터={}",
                targets.size(), request.hasDistrict() ? request.lawdCode() : "전체",
                request.refresh(), properties.maxCallsPerRun(), properties.display(),
                properties.filter());

        return collect(targets, request);
    }

    private PlaceImageResult collect(List<Attraction> targets, PlaceImageJobRequest request) {
        Set<String> alreadyAsked = request.refresh() ? Set.of() : alreadyAsked(targets);

        int skipped = 0;
        int calls = 0;
        int filled = 0;
        int none = 0;
        int failed = 0;
        String stoppedReason = null;

        for (Attraction attraction : targets) {
            if (alreadyAsked.contains(attraction.getContentId())) {
                skipped++;
                continue;
            }

            String query = queryRule.build(attraction.getName(), regionName(attraction));

            if (query == null) {
                // 이름이 없으면 무엇을 검색할지 말할 수 없다. 값을 지어내지 않고 건너뛴다.
                log.warn("검색어를 만들 수 없어 건너뜁니다: contentId={}", attraction.getContentId());
                skipped++;
                continue;
            }

            if (calls >= properties.maxCallsPerRun()) {
                stoppedReason = "한 실행의 호출 상한(%d)에 도달".formatted(properties.maxCallsPerRun());
                break;
            }

            NaverImageSearchResponse response;
            try {
                // 세는 것은 "부르려고 한 횟수" 다. 실패한 호출도 한도를 깎으므로 실패 경로에서
                // 빠지면 보고된 호출 수가 실제보다 적어진다.
                calls++;
                response = imageSearchClient.search(query, properties.display(), properties.filter());
            } catch (NaverAuthenticationException e) {
                stoppedReason = "네이버 인증 실패";
                log.error("네이버 인증에 실패해 대표 사진 보강을 멈춥니다. 키가 맞는지, "
                        + "NAVER API HUB 콘솔에서 이 Application 에 이미지 검색이 켜져 있는지 "
                        + "확인하세요. 남은 관광지는 다음 실행이 봅니다.", e);
                break;
            } catch (NaverRateLimitException e) {
                stoppedReason = "네이버 호출 한도 초과";
                log.error("네이버 호출 한도를 넘어 대표 사진 보강을 멈춥니다. "
                        + "남은 관광지는 다음 실행이 봅니다.", e);
                break;
            } catch (ExternalApiException e) {
                // 이 관광지 하나의 실패다. 실패했다는 사실을 남겨 다음 실행이 또 부르지 않게 한다.
                log.warn("이미지 검색에 실패해 이 관광지는 건너뜁니다: {} ({})",
                        attraction.getName(), query, e);
                writer.record(PlaceImage.failed(attraction.getContentId(),
                        NaverImageSearchClient.searchPageUrl(query), ImageSource.NAVER_IMAGE,
                        LocalDateTime.now()));
                failed++;
                sleepBetweenCalls();
                continue;
            }

            if (record(attraction, query, response)) {
                filled++;
            } else {
                none++;
            }

            sleepBetweenCalls();
        }

        PlaceImageResult result = new PlaceImageResult(targets.size(), skipped, calls, filled,
                none, failed, stoppedReason);

        log.info("대표 사진 보강 완료: {}", result.summary());

        return result;
    }

    /**
     * 찾은 것을 표에 남긴다.
     *
     * @return 쓸 수 있는 사진을 남겼으면 true, 결과가 없어 {@code NONE} 으로 남겼으면 false
     */
    private boolean record(Attraction attraction, String query, NaverImageSearchResponse response) {
        String sourceUrl = NaverImageSearchClient.searchPageUrl(query);
        LocalDateTime fetchedAt = LocalDateTime.now();

        Optional<NaverImageSearchResponse.Item> first = response.firstUsable();

        if (first.isEmpty()) {
            writer.record(PlaceImage.none(attraction.getContentId(), sourceUrl,
                    ImageSource.NAVER_IMAGE, fetchedAt));

            return false;
        }

        String imageUrl = PlaceImage.fitOrNull(first.get().imageUrl());
        String thumbnailUrl = PlaceImage.fitOrNull(first.get().thumbnailUrl());

        if (imageUrl == null && thumbnailUrl == null) {
            // 컬럼에 담기지 않는 주소뿐이다. 잘라서 넣으면 열리지 않는 주소가 되므로 없는 것으로 본다.
            log.warn("찾은 주소가 컬럼({}자)에 담기지 않아 결과 없음으로 남깁니다: {}",
                    PlaceImage.MAX_URL_LENGTH, query);
            writer.record(PlaceImage.none(attraction.getContentId(), sourceUrl,
                    ImageSource.NAVER_IMAGE, fetchedAt));

            return false;
        }

        writer.record(PlaceImage.found(attraction.getContentId(), thumbnailUrl, imageUrl,
                PlaceImage.fitOrNull(sourceUrl), ImageSource.NAVER_IMAGE, fetchedAt));

        return true;
    }

    /** 이미 물어본 적이 있는 관광지. 상태와 무관하다 — 행이 있으면 물어본 것이다. */
    private Set<String> alreadyAsked(List<Attraction> targets) {
        Set<String> contentIds = new HashSet<>();

        for (Attraction attraction : targets) {
            contentIds.add(attraction.getContentId());
        }

        if (contentIds.isEmpty()) {
            return Set.of();
        }

        Set<String> asked = new HashSet<>();

        for (PlaceImage placeImage : placeImageRepository.findAllByContentIdIn(contentIds)) {
            asked.add(placeImage.getContentId());
        }

        return asked;
    }

    private static String regionName(Attraction attraction) {
        return attraction.getRegionCode() == null ? null : attraction.getRegionCode().getName();
    }

    private void sleepBetweenCalls() {
        Duration delay = naverProperties.callDelay();

        if (delay == null || delay.isZero() || delay.isNegative()) {
            return;
        }

        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("대표 사진 보강이 중단되었습니다.", e);
        }
    }
}
