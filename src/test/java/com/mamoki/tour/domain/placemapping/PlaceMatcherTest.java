package com.mamoki.tour.domain.placemapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.placemapping.entity.PlaceMapping;
import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.placemapping.enums.PlaceMappingStatus;
import com.mamoki.tour.domain.placemapping.enums.PlaceMatchMethod;
import com.mamoki.tour.domain.placemapping.repository.PlaceMappingRepository;
import com.mamoki.tour.domain.placemapping.service.PlaceMatcher;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.global.enums.DataStatus;

/**
 * 원천 이름을 카탈로그에 잇는 단 하나의 진입점.
 *
 * <p>여기서 확인하는 것은 두 가지다. 확정 매핑이 실제로 매칭에 실리는 것, 그리고 확정이
 * 아닌 행은 어떤 경로로도 값을 만들지 못하는 것이다. 두 번째가 깨지면 틀린 장소에 입장객
 * 수나 검색순위가 붙는데 에러가 나지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PlaceMatcherTest {

    private static final String GANGNEUNG = "51150";
    private static final String CHUNCHEON = "51110";

    @Autowired
    private PlaceMatcher placeMatcher;

    @Autowired
    private PlaceMappingRepository placeMappingRepository;

    @Autowired
    private AttractionRepository attractionRepository;

    @Autowired
    private RegionCodeRepository regionCodeRepository;

    @BeforeEach
    void reset() {
        placeMappingRepository.deleteAllInBatch();
        attractionRepository.deleteAllInBatch();
    }

    private void saveAttraction(String contentId, String name, String lawdCode) {
        RegionCode region = regionCodeRepository.findByLawdCode(lawdCode).orElseThrow();

        attractionRepository.save(Attraction.builder()
                .contentId(contentId)
                .name(name)
                .regionCode(region)
                .dataStatus(DataStatus.AVAILABLE)
                .baseAt(LocalDateTime.now())
                .source("KorService2")
                .build());
    }

    private void saveMapping(MappingSource source, String sourceName, String lawdCode,
                             String contentId, PlaceMappingStatus status, PlaceMatchMethod method) {
        placeMappingRepository.save(PlaceMapping.builder()
                .source(source)
                .sourceName(sourceName)
                .normalizedName(PlaceNameNormalizer.normalize(sourceName))
                .lawdCode(lawdCode)
                .contentId(contentId)
                .method(method)
                .status(status)
                .decidedAt(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("이름으로 이어지는 장소는 매핑이 없어도 그대로 이어진다")
    void keepsMatchingByNameWithoutMapping() {
        saveAttraction("125266", "경포해변", GANGNEUNG);

        assertThat(placeMatcher.index(MappingSource.VISITOR_STATS).match("강릉시", "경포해변"))
                .contains("125266");
    }

    @Test
    @DisplayName("이름으로 못 찾은 장소를 확정 매핑이 잇는다")
    void confirmedMappingFillsTheGap() {
        saveAttraction("125266", "경포해변", GANGNEUNG);
        saveMapping(MappingSource.VISITOR_STATS, "경포해수욕장", GANGNEUNG, "125266",
                PlaceMappingStatus.CONFIRMED, PlaceMatchMethod.KAKAO_COORD);

        assertThat(placeMatcher.index(MappingSource.VISITOR_STATS).match("강릉시", "경포해수욕장"))
                .contains("125266");
    }

    @Test
    @DisplayName("저신뢰 매핑은 조회에 값을 만들지 않는다")
    void lowConfidenceMappingMakesNoValue() {
        saveAttraction("125266", "경포해변", GANGNEUNG);
        saveMapping(MappingSource.VISITOR_STATS, "경포해수욕장", GANGNEUNG, "125266",
                PlaceMappingStatus.LOW_CONFIDENCE, null);

        assertThat(placeMatcher.index(MappingSource.VISITOR_STATS).match("강릉시", "경포해수욕장"))
                .isEmpty();
        assertThat(placeMatcher.normalizedAliases(
                MappingSource.RELATED_PLACE, "125266", "경포해변"))
                .containsExactly(PlaceNameNormalizer.normalize("경포해변"));
    }

    @Test
    @DisplayName("미매칭 매핑도 값을 만들지 않는다")
    void unmatchedMappingMakesNoValue() {
        saveAttraction("125266", "경포해변", GANGNEUNG);
        saveMapping(MappingSource.VISITOR_STATS, "있을리없는이름", GANGNEUNG, null,
                PlaceMappingStatus.UNMATCHED, null);

        assertThat(placeMatcher.index(MappingSource.VISITOR_STATS).match("강릉시", "있을리없는이름"))
                .isEmpty();
    }

    @Test
    @DisplayName("원천이 다른 매핑은 서로 쓰이지 않는다")
    void mappingsDoNotLeakAcrossSources() {
        saveMapping(MappingSource.VISITOR_STATS, "경포해수욕장", GANGNEUNG, "125266",
                PlaceMappingStatus.CONFIRMED, PlaceMatchMethod.KAKAO_COORD);

        assertThat(placeMatcher.index(MappingSource.TMAP).match("강릉시", "경포해수욕장")).isEmpty();
    }

    @Test
    @DisplayName("입장객통계는 시·군이 다르면 확정 매핑이라도 잇지 않는다")
    void visitorStatsMappingStaysInsideItsRegion() {
        saveMapping(MappingSource.VISITOR_STATS, "경포해수욕장", GANGNEUNG, "125266",
                PlaceMappingStatus.CONFIRMED, PlaceMatchMethod.KAKAO_COORD);

        assertThat(placeMatcher.index(MappingSource.VISITOR_STATS).match("춘천시", "경포해수욕장"))
                .isEmpty();
    }

    @Test
    @DisplayName("이름만으로 찾는 원천에서 같은 이름이 다른 관광지를 가리키면 값을 만들지 않는다")
    void dropsAmbiguousNameForNameOnlySource() {
        saveMapping(MappingSource.TMAP, "해변공원", GANGNEUNG, "1",
                PlaceMappingStatus.CONFIRMED, PlaceMatchMethod.KAKAO_COORD);
        saveMapping(MappingSource.TMAP, "해변공원", CHUNCHEON, "2",
                PlaceMappingStatus.CONFIRMED, PlaceMatchMethod.KAKAO_COORD);

        assertThat(placeMatcher.index(MappingSource.TMAP).match("강릉시", "해변공원")).isEmpty();
    }

    @Test
    @DisplayName("카탈로그 이름이 먼저고 매핑은 그 뒤를 잇는다")
    void catalogNameWinsOverMapping() {
        saveAttraction("125266", "경포해변", GANGNEUNG);
        // 카카오가 잘못 골라 같은 이름이 다른 관광지로 매핑되어도 이름 매칭을 덮지 않는다.
        saveMapping(MappingSource.VISITOR_STATS, "경포해변", GANGNEUNG, "999999",
                PlaceMappingStatus.CONFIRMED, PlaceMatchMethod.KAKAO_COORD);

        assertThat(placeMatcher.index(MappingSource.VISITOR_STATS).match("강릉시", "경포해변"))
                .contains("125266");
    }

    @Test
    @DisplayName("한 관광지를 가리키는 원천 쪽 이름들을 카탈로그 이름과 함께 돌려준다")
    void listsSourceNamesForOneAttraction() {
        saveMapping(MappingSource.RELATED_PLACE, "경포해수욕장", GANGNEUNG, "125266",
                PlaceMappingStatus.CONFIRMED, PlaceMatchMethod.NORMALIZED);

        assertThat(placeMatcher.normalizedAliases(MappingSource.RELATED_PLACE, "125266", "경포해변"))
                .containsExactly(PlaceNameNormalizer.normalize("경포해변"),
                        PlaceNameNormalizer.normalize("경포해수욕장"));
    }

    @Test
    @DisplayName("운영자가 넣은 행도 확정이면 그대로 쓰인다")
    void honoursManualRows() {
        saveMapping(MappingSource.TMAP, "강원랜드카지노", GANGNEUNG, "125266",
                PlaceMappingStatus.CONFIRMED, PlaceMatchMethod.MANUAL);

        assertThat(placeMatcher.index(MappingSource.TMAP).match("강릉시", "강원랜드카지노"))
                .contains("125266");
    }
}
