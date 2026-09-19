package com.mamoki.tour.domain.placemapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.placemapping.entity.PlaceMapping;
import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.placemapping.enums.PlaceMappingStatus;
import com.mamoki.tour.domain.placemapping.enums.PlaceMatchMethod;
import com.mamoki.tour.domain.placemapping.importer.PlaceMappingJobService;
import com.mamoki.tour.domain.placemapping.importer.PlaceMappingResult;
import com.mamoki.tour.domain.placemapping.repository.PlaceMappingRepository;
import com.mamoki.tour.domain.placemapping.service.PlaceMatcher;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankEntry;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankSnapshot;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankEntryRepository;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankSnapshotRepository;
import com.mamoki.tour.global.enums.CatalogMatchStatus;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.enums.SnapshotStatus;
import com.mamoki.tour.infra.kakao.KakaoLocalClient;
import com.mamoki.tour.infra.kakao.KakaoRateLimitException;
import com.mamoki.tour.infra.kakao.dto.KakaoKeywordSearchResponse;
import com.mamoki.tour.infra.kakao.dto.KakaoPlace;

/**
 * 미매칭 이름을 카카오로 찾아 매핑 표에 남기는 배치.
 *
 * <p>카카오 호출은 흉내 낸다. 판정 규칙 자체는 {@code PlaceMappingDeciderTest} 가 순수
 * 계산으로 확인하므로, 여기서는 배치가 한도와 사람의 판단을 어떻게 다루는지만 본다.
 * 조용히 틀리는 쪽은 둘이다 — 확정한 이름을 다시 불러 한도를 깎는 것, 그리고 운영자가
 * 넣은 행을 자동 판정이 되돌리는 것.
 */
@SpringBootTest
@ActiveProfiles("test")
class PlaceMappingJobServiceTest {

    private static final String GANGNEUNG = "51150";

    /** 경포해수욕장의 실제 카카오 좌표. */
    private static final String KAKAO_LATITUDE = "37.8034";
    private static final String KAKAO_LONGITUDE = "128.9102";

    @MockitoBean
    private KakaoLocalClient kakaoLocalClient;

    @Autowired
    private PlaceMappingJobService jobService;

    @Autowired
    private PlaceMappingRepository placeMappingRepository;

    @Autowired
    private PlaceMatcher placeMatcher;

    @Autowired
    private AttractionRepository attractionRepository;

    @Autowired
    private RegionCodeRepository regionCodeRepository;

    @Autowired
    private TmapRankSnapshotRepository snapshotRepository;

    @Autowired
    private TmapRankEntryRepository entryRepository;

    @BeforeEach
    void reset() {
        entryRepository.deleteAllInBatch();
        snapshotRepository.deleteAllInBatch();
        placeMappingRepository.deleteAllInBatch();
        attractionRepository.deleteAllInBatch();

        given(kakaoLocalClient.hasCredentials()).willReturn(true);
        given(kakaoLocalClient.callDelay()).willReturn(Duration.ZERO);
        givenKakaoFinds("경포해수욕장");
    }

    private void givenKakaoFinds(String placeName) {
        KakaoPlace place = new KakaoPlace("8199114", placeName, "AT4", null,
                "강원특별자치도 강릉시 강문동 산 1", null, KAKAO_LONGITUDE, KAKAO_LATITUDE);

        given(kakaoLocalClient.searchKeyword(anyString(), any(), any()))
                .willReturn(new KakaoKeywordSearchResponse(List.of(place), null));
    }

    /** 카카오 좌표 바로 옆에 있는 카탈로그 관광지. 경계(300m) 안에 이 한 곳만 둔다. */
    private void saveNearbyAttraction(String contentId, String name) {
        RegionCode region = regionCodeRepository.findByLawdCode(GANGNEUNG).orElseThrow();

        attractionRepository.save(Attraction.builder()
                .contentId(contentId)
                .name(name)
                .latitude(new BigDecimal(KAKAO_LATITUDE))
                .longitude(new BigDecimal(KAKAO_LONGITUDE))
                .regionCode(region)
                .dataStatus(DataStatus.AVAILABLE)
                .baseAt(LocalDateTime.now())
                .source("KorService2")
                .build());
    }

    /** 활성 TMAP 스냅샷에 미매칭 행 하나를 심는다. 배치가 볼 대상이 된다. */
    private void saveUnmatchedTmapRow(String placeName) {
        TmapRankSnapshot snapshot = snapshotRepository.save(TmapRankSnapshot.builder()
                .version("test-" + System.nanoTime())
                .sourcePeriod("202508-202607")
                .downloadedOn(LocalDate.of(2026, 9, 6))
                .importedAt(LocalDateTime.now())
                .status(SnapshotStatus.IMPORTING)
                .sourceFileName("test")
                .rowCount(0)
                .build());

        entryRepository.save(TmapRankEntry.builder()
                .snapshot(snapshot)
                .rawRegionName("강릉시")
                .rawPlaceName(placeName)
                .normalizedName(PlaceNameNormalizer.normalize(placeName))
                .searchRatio(BigDecimal.ONE)
                .sourceRank(1)
                .matchStatus(CatalogMatchStatus.UNMATCHED)
                .build());

        snapshot.activate(1);
        snapshotRepository.save(snapshot);
    }

    private PlaceMapping onlyMapping() {
        List<PlaceMapping> mappings = placeMappingRepository.findAllBySource(MappingSource.TMAP);

        assertThat(mappings).hasSize(1);
        return mappings.get(0);
    }

    @Test
    @DisplayName("키가 비어 있으면 호출을 한 번도 내지 않고 이유를 남긴다")
    void stopsBeforeTheFirstCallWithoutKey() {
        given(kakaoLocalClient.hasCredentials()).willReturn(false);
        saveUnmatchedTmapRow("경포해변");

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.kakaoCalls()).isZero();
        assertThat(result.stoppedReason()).contains("KAKAO_REST_API_KEY");
        Mockito.verify(kakaoLocalClient, Mockito.never()).searchKeyword(anyString(), any(), any());
        assertThat(placeMappingRepository.count()).isZero();
    }

    @Test
    @DisplayName("경계 안 한 곳으로 좁혀지면 확정하고 그 매핑이 곧바로 매칭에 실린다")
    void confirmsAndFeedsTheMatcher() {
        saveNearbyAttraction("126508", "경포해수욕장");
        saveUnmatchedTmapRow("경포해변");

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.confirmed()).isEqualTo(1);
        assertThat(result.kakaoCalls()).isEqualTo(1);
        assertThat(onlyMapping().getStatus()).isEqualTo(PlaceMappingStatus.CONFIRMED);
        assertThat(placeMatcher.index(MappingSource.TMAP).match("강릉시", "경포해변"))
                .contains("126508");
    }

    @Test
    @DisplayName("이을 후보가 없으면 미매칭으로 남기고 값을 만들지 않는다")
    void leavesUnmatchedWithoutCandidate() {
        saveUnmatchedTmapRow("강릉컨트리클럽");

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.unmatched()).isEqualTo(1);
        assertThat(onlyMapping().getStatus()).isEqualTo(PlaceMappingStatus.UNMATCHED);
        assertThat(placeMatcher.index(MappingSource.TMAP).match("강릉시", "강릉컨트리클럽")).isEmpty();
    }

    @Test
    @DisplayName("다시 실행해도 확정한 이름은 부르지 않는다")
    void doesNotCallAgainForConfirmedNames() {
        saveNearbyAttraction("126508", "경포해수욕장");
        saveUnmatchedTmapRow("경포해변");

        jobService.run(MappingSource.TMAP);
        PlaceMappingResult second = jobService.run(MappingSource.TMAP);

        assertThat(second.kakaoCalls()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
        // 두 번 돌아도 행이 늘지 않고 결과도 같다.
        assertThat(onlyMapping().getContentId()).isEqualTo("126508");
        Mockito.verify(kakaoLocalClient, Mockito.times(1))
                .searchKeyword(anyString(), any(), any());
    }

    @Test
    @DisplayName("좁히지 못한 이름은 다음 실행이 다시 본다")
    void retriesNamesThatWereNotSettled() {
        saveUnmatchedTmapRow("강릉컨트리클럽");

        jobService.run(MappingSource.TMAP);
        PlaceMappingResult second = jobService.run(MappingSource.TMAP);

        assertThat(second.kakaoCalls()).isEqualTo(1);
        assertThat(second.skipped()).isZero();
    }

    @Test
    @DisplayName("운영자가 넣은 행은 배치가 덮지 않는다")
    void neverOverwritesManualRows() {
        saveNearbyAttraction("126508", "경포해수욕장");
        saveUnmatchedTmapRow("경포해변");
        placeMappingRepository.save(PlaceMapping.builder()
                .source(MappingSource.TMAP)
                .sourceName("경포해변")
                .normalizedName(PlaceNameNormalizer.normalize("경포해변"))
                .lawdCode(GANGNEUNG)
                .contentId("999999")
                .method(PlaceMatchMethod.MANUAL)
                .status(PlaceMappingStatus.CONFIRMED)
                .decidedAt(LocalDateTime.now())
                .build());

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.kakaoCalls()).isZero();
        assertThat(onlyMapping().getContentId()).isEqualTo("999999");
        assertThat(onlyMapping().getMethod()).isEqualTo(PlaceMatchMethod.MANUAL);
    }

    @Test
    @DisplayName("운영자가 저신뢰로 표시해 둔 행도 자동 판정이 확정으로 바꾸지 않는다")
    void neverRevivesManualLowConfidenceRows() {
        saveNearbyAttraction("126508", "경포해수욕장");
        saveUnmatchedTmapRow("경포해변");
        placeMappingRepository.save(PlaceMapping.builder()
                .source(MappingSource.TMAP)
                .sourceName("경포해변")
                .normalizedName(PlaceNameNormalizer.normalize("경포해변"))
                .lawdCode(GANGNEUNG)
                .contentId(null)
                .method(PlaceMatchMethod.MANUAL)
                .status(PlaceMappingStatus.LOW_CONFIDENCE)
                .decidedAt(LocalDateTime.now())
                .build());

        jobService.run(MappingSource.TMAP);

        assertThat(onlyMapping().getStatus()).isEqualTo(PlaceMappingStatus.LOW_CONFIDENCE);
        assertThat(onlyMapping().getContentId()).isNull();
    }

    @Test
    @DisplayName("한도를 넘으면 그 자리에서 멈추고 멈춘 이유를 알린다")
    void stopsOnRateLimit() {
        saveUnmatchedTmapRow("경포해변");
        willThrow(new KakaoRateLimitException("한도 초과"))
                .given(kakaoLocalClient).searchKeyword(anyString(), any(), any());

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.stoppedEarly()).isTrue();
        assertThat(result.stoppedReason()).contains("한도");
        assertThat(placeMappingRepository.count()).isZero();
    }
}
