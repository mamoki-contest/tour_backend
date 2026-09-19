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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.mamoki.tour.domain.placemapping.enums.UnmatchedCategory;
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
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.enums.SnapshotStatus;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.kakao.KakaoAuthenticationException;
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

    /** 카카오 좌표에서 멀리 떨어진 카탈로그 관광지. 거리로는 좁혀지지 않는다. */
    private void saveDistantAttraction(String contentId, String name) {
        RegionCode region = regionCodeRepository.findByLawdCode(GANGNEUNG).orElseThrow();

        attractionRepository.save(Attraction.builder()
                .contentId(contentId)
                .name(name)
                // 위도 1도는 약 111km 다. 0.2 도면 20km 를 넘어 어떤 경계에도 걸리지 않는다.
                .latitude(new BigDecimal(KAKAO_LATITUDE).add(new BigDecimal("0.2")))
                .longitude(new BigDecimal(KAKAO_LONGITUDE))
                .regionCode(region)
                .dataStatus(DataStatus.AVAILABLE)
                .baseAt(LocalDateTime.now())
                .source("KorService2")
                .build());
    }

    /** 카카오가 분류까지 실어 돌려주게 한다. 재분류(#72)가 보는 값이다. */
    private void givenKakaoFinds(String placeName, String categoryGroupCode, String categoryName) {
        KakaoPlace place = new KakaoPlace("8199114", placeName, categoryGroupCode, categoryName,
                "강원특별자치도 강릉시 강문동 산 1", null, KAKAO_LONGITUDE, KAKAO_LATITUDE);

        given(kakaoLocalClient.searchKeyword(anyString(), any(), any()))
                .willReturn(new KakaoKeywordSearchResponse(List.of(place), null));
    }

    /** 카카오가 아무것도 찾지 못하게 한다. 표기 차이 경로는 카카오 없이도 서야 한다. */
    private void givenKakaoFindsNothing() {
        given(kakaoLocalClient.searchKeyword(anyString(), any(), any()))
                .willReturn(new KakaoKeywordSearchResponse(List.of(), null));
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

    /**
     * 활성 TMAP 스냅샷 하나에 여러 행을 심는다.
     *
     * @param matchedContentIdByName 이미 이어진 행. 값이 null 이면 미매칭 행이다.
     */
    private void saveTmapSnapshot(LinkedHashMap<String, String> matchedContentIdByName) {
        TmapRankSnapshot snapshot = snapshotRepository.save(TmapRankSnapshot.builder()
                .version("test-" + System.nanoTime())
                .sourcePeriod("202508-202607")
                .downloadedOn(LocalDate.of(2026, 9, 6))
                .importedAt(LocalDateTime.now())
                .status(SnapshotStatus.IMPORTING)
                .sourceFileName("test")
                .rowCount(0)
                .build());

        int rank = 1;

        for (Map.Entry<String, String> row : matchedContentIdByName.entrySet()) {
            entryRepository.save(TmapRankEntry.builder()
                    .snapshot(snapshot)
                    .rawRegionName("강릉시")
                    .rawPlaceName(row.getKey())
                    .normalizedName(PlaceNameNormalizer.normalize(row.getKey()))
                    .searchRatio(BigDecimal.ONE)
                    .sourceRank(rank++)
                    .contentId(row.getValue())
                    .matchStatus(row.getValue() == null
                            ? CatalogMatchStatus.UNMATCHED : CatalogMatchStatus.MATCHED)
                    .build());
        }

        snapshot.activate(matchedContentIdByName.size());
        snapshotRepository.save(snapshot);
    }

    private PlaceMapping mappingOf(String sourceName) {
        return placeMappingRepository.findAllBySource(MappingSource.TMAP).stream()
                .filter(mapping -> mapping.getSourceName().equals(sourceName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("판정이 남지 않았습니다: " + sourceName));
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

    @Test
    @DisplayName("표기만 걷어내면 카탈로그와 같아지는 이름은 카카오 없이도 확정한다 (#72)")
    void promotesNameVariantWithoutKakao() {
        givenKakaoFindsNothing();
        saveDistantAttraction("126508", "사근진해변(사근진해수욕장)");
        saveUnmatchedTmapRow("사근진해변");

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.confirmed()).isEqualTo(1);
        assertThat(result.nameVariant()).isEqualTo(1);
        assertThat(onlyMapping().getStatus()).isEqualTo(PlaceMappingStatus.CONFIRMED);
        assertThat(onlyMapping().getMethod()).isEqualTo(PlaceMatchMethod.NAME_VARIANT);
        assertThat(onlyMapping().getUnmatchedCategory()).isEqualTo(UnmatchedCategory.NAME_VARIANT);
        assertThat(placeMatcher.index(MappingSource.TMAP).match("강릉시", "사근진해변"))
                .contains("126508");
    }

    @Test
    @DisplayName("같은 카탈로그를 가리키는 원천 이름이 둘이면 둘 다 확정하지 않는다 (#72·#84)")
    void doesNotPromoteWhenTwoSourceNamesPointAtTheSameCatalog() {
        // 둘 다 확정하면 한 관광지에 TMAP 순위 두 개가 붙고, 조회는 그중 아무거나 보여 준다.
        givenKakaoFindsNothing();
        saveDistantAttraction("126508", "휘닉스 파크");

        LinkedHashMap<String, String> rows = new LinkedHashMap<>();
        rows.put("휘닉스파크(골프장)", null);
        rows.put("휘닉스파크(워터파크)", null);
        saveTmapSnapshot(rows);

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.confirmed()).isZero();
        assertThat(result.lowConfidence()).isEqualTo(2);
        assertThat(mappingOf("휘닉스파크(골프장)").getUnmatchedCategory())
                .isEqualTo(UnmatchedCategory.NAME_VARIANT);
        assertThat(mappingOf("휘닉스파크(골프장)").getContentId()).isNull();
        assertThat(placeMatcher.index(MappingSource.TMAP).match("강릉시", "휘닉스파크(골프장)"))
                .isEmpty();
    }

    @Test
    @DisplayName("이미 다른 행이 이어진 관광지에는 표기 차이로 두 번째 행을 잇지 않는다 (#84)")
    void doesNotPromoteOntoAnAlreadyMatchedAttraction() {
        givenKakaoFindsNothing();
        saveDistantAttraction("126508", "강릉 죽서루");

        LinkedHashMap<String, String> rows = new LinkedHashMap<>();
        rows.put("강릉 죽서루", "126508");
        rows.put("죽서루", null);
        saveTmapSnapshot(rows);

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.confirmed()).isZero();
        assertThat(mappingOf("죽서루").getStatus()).isEqualTo(PlaceMappingStatus.LOW_CONFIDENCE);
        assertThat(mappingOf("죽서루").getUnmatchedCategory())
                .isEqualTo(UnmatchedCategory.NAME_VARIANT);
    }

    @Test
    @DisplayName("카테고리와 이름 접미어가 둘 다 맞으면 카탈로그 대상이 아니라고 적는다 (#72)")
    void marksOutOfCatalogWithBothSignals() {
        givenKakaoFinds("강릉컨트리클럽", null, "스포츠,레저 > 골프 > 골프장");
        saveUnmatchedTmapRow("강릉컨트리클럽");

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.outOfCatalog()).isEqualTo(1);
        assertThat(onlyMapping().getUnmatchedCategory()).isEqualTo(UnmatchedCategory.OUT_OF_CATALOG);
        assertThat(onlyMapping().getCategoryRule()).isEqualTo("스포츠,레저 > 골프");
        assertThat(onlyMapping().getNameSuffixRule()).isEqualTo("컨트리클럽");
        assertThat(onlyMapping().getKakaoCategoryName()).isEqualTo("스포츠,레저 > 골프 > 골프장");
    }

    @Test
    @DisplayName("카테고리만 맞으면 모르는 것으로 남겨 분모에 둔다 (#72)")
    void keepsUnknownWhenOnlyTheCategoryMatches() {
        givenKakaoFinds("강릉수목원", null, "스포츠,레저 > 골프 > 골프장");
        saveUnmatchedTmapRow("강릉수목원");

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.outOfCatalog()).isZero();
        assertThat(result.unknown()).isEqualTo(1);
        assertThat(onlyMapping().getUnmatchedCategory()).isEqualTo(UnmatchedCategory.UNKNOWN);
    }

    @Test
    @DisplayName("카탈로그가 담고 있는 종류는 접미어 규칙이 꺼져 분모에 남는다 (#72)")
    void keepsKindsThatTheCatalogItselfCarries() {
        // KorService2 는 골프장을 레포츠로 담는다. 담고 있는 종류를 "대상이 아니다" 라고
        // 부르면 분모가 줄어 매칭률만 좋아진다.
        givenKakaoFinds("강릉컨트리클럽", null, "스포츠,레저 > 골프 > 골프장");
        saveDistantAttraction("126900", "동강시스타컨트리클럽");
        saveUnmatchedTmapRow("강릉컨트리클럽");

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.outOfCatalog()).isZero();
        assertThat(onlyMapping().getUnmatchedCategory()).isEqualTo(UnmatchedCategory.UNKNOWN);
    }

    @Test
    @DisplayName("매칭률을 분모 정리 전과 후로 함께 낸다 (#72)")
    void reportsMatchRateBeforeAndAfterExclusion() {
        givenKakaoFinds("강릉컨트리클럽", null, "스포츠,레저 > 골프 > 골프장");
        saveDistantAttraction("126508", "경포해수욕장");

        LinkedHashMap<String, String> rows = new LinkedHashMap<>();
        rows.put("경포해수욕장", "126508");
        rows.put("강릉컨트리클럽", null);
        saveTmapSnapshot(rows);

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.matchRate().totalRows()).isEqualTo(2);
        assertThat(result.matchRate().matchedRows()).isEqualTo(1);
        assertThat(result.matchRate().outOfCatalogRows()).isEqualTo(1);
        assertThat(result.matchRate().beforeExclusion()).isEqualTo(0.5);
        assertThat(result.matchRate().afterExclusion()).isEqualTo(1.0);
        assertThat(result.matchRate().summary()).contains("제외 전").contains("제외 후");
    }

    @Test
    @DisplayName("지난 실행이 확정한 이름도 매칭률의 분자에 든다 (#72)")
    void countsMappingsConfirmedByEarlierRuns() {
        saveNearbyAttraction("126508", "경포해수욕장");
        saveUnmatchedTmapRow("경포해변");

        jobService.run(MappingSource.TMAP);
        PlaceMappingResult second = jobService.run(MappingSource.TMAP);

        assertThat(second.confirmed()).isZero();
        assertThat(second.matchRate().matchedRows()).isEqualTo(1);
        assertThat(second.matchRate().beforeExclusion()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("호출이 연달아 실패해도 호출 간격을 지킨다 (#83)")
    void keepsTheCallIntervalWhenSearchFails() {
        LinkedHashMap<String, String> rows = new LinkedHashMap<>();
        rows.put("경포해변", null);
        rows.put("사근진해변", null);
        saveTmapSnapshot(rows);

        willThrow(new ExternalApiException(ApiProvider.KAKAO_LOCAL, "서버 오류"))
                .given(kakaoLocalClient).searchKeyword(anyString(), any(), any());

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.kakaoCalls()).isEqualTo(2);
        // 간격을 묻지도 않고 다음 이름으로 달리면 한 실행의 호출 상한까지 전속력이 된다.
        Mockito.verify(kakaoLocalClient, Mockito.times(2)).callDelay();
    }

    @Test
    @DisplayName("인증 실패로 멈춰도 그 호출은 셈에 든다 (#83)")
    void countsTheFailedAuthenticationCall() {
        saveUnmatchedTmapRow("경포해변");
        willThrow(new KakaoAuthenticationException("인증 실패"))
                .given(kakaoLocalClient).searchKeyword(anyString(), any(), any());

        PlaceMappingResult result = jobService.run(MappingSource.TMAP);

        assertThat(result.kakaoCalls()).isEqualTo(1);
        assertThat(result.stoppedReason()).contains("인증");
    }
}
