package com.mamoki.tour.domain.placemapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.cache.repository.ExternalApiCacheRepository;
import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.placemapping.importer.UnmatchedNameCollector;
import com.mamoki.tour.domain.placemapping.importer.UnmatchedPlaceName;
import com.mamoki.tour.domain.placemapping.repository.PlaceMappingRepository;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.infra.tarrltetar.TarRlteTarClient;
import com.mamoki.tour.infra.tarrltetar.TarRlteTarProperties;

import java.util.List;

/**
 * 연관 장소 원천에서 판정 대상 이름을 모으는 과정.
 *
 * <p>다른 두 원천과 달리 스냅샷이 없어 공급자를 직접 부른다. 처음 부르는 시·군은 응답을
 * 캐시에 적는데, 이 과정을 읽기 전용 트랜잭션으로 묶으면 첫 실행이 통째로 실패한다.
 * 개발 스키마 실행에서 실제로 이렇게 깨졌고, 캐시가 이미 채워진 환경에서는 드러나지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RelatedPlaceMappingCollectTest {

    private static final String GANGNEUNG = "51150";

    @MockitoBean
    private TarRlteTarClient tarRlteTarClient;

    @Autowired
    private List<UnmatchedNameCollector> collectors;

    @Autowired
    private ExternalApiCacheRepository cacheRepository;

    @Autowired
    private AttractionRepository attractionRepository;

    @Autowired
    private RegionCodeRepository regionCodeRepository;

    @Autowired
    private PlaceMappingRepository placeMappingRepository;

    private UnmatchedNameCollector collector() {
        return collectors.stream()
                .filter(candidate -> candidate.source() == MappingSource.RELATED_PLACE)
                .findFirst()
                .orElseThrow();
    }

    @BeforeEach
    void setUp() {
        cacheRepository.deleteAllInBatch();
        placeMappingRepository.deleteAllInBatch();
        attractionRepository.deleteAllInBatch();

        given(tarRlteTarClient.relatedListKey(anyString(), anyInt()))
                .willAnswer(call -> "relatedList?signguCd=" + call.getArgument(0)
                        + "&pageNo=" + call.getArgument(1));
        given(tarRlteTarClient.rowsPerPage()).willReturn(1_000);
        given(tarRlteTarClient.maxPages()).willReturn(1);
        given(tarRlteTarClient.baseYm()).willReturn("202607");
        given(tarRlteTarClient.parse(anyString()))
                .willAnswer(call -> realClient().parse(call.getArgument(0)));

        // 강릉만 응답을 주고 나머지 시·군은 빈 응답으로 둔다.
        given(tarRlteTarClient.relatedListJson(anyString(), anyInt()))
                .willAnswer(call -> GANGNEUNG.equals(call.getArgument(0)) ? body() : emptyBody());
    }

    private TarRlteTarClient realClient() {
        return new TarRlteTarClient(new TarRlteTarProperties(
                "http://example.invalid", "key", "tour", "202607", 1_000, 1,
                Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    private void saveAttraction(String contentId, String name) {
        RegionCode region = regionCodeRepository.findByLawdCode(GANGNEUNG).orElseThrow();

        attractionRepository.save(Attraction.builder()
                .contentId(contentId)
                .name(name)
                .regionCode(region)
                .dataStatus(DataStatus.AVAILABLE)
                .baseAt(LocalDateTime.now())
                .source("KorService2")
                .build());
    }

    @Test
    @DisplayName("캐시가 비어 있어도 공급자 응답을 받아 대상 이름을 모은다")
    void collectsWhenCacheIsEmpty() {
        // 이름으로 이미 이어지는 오죽헌은 대상이 아니고, 경포해수욕장만 판정 대상이다.
        saveAttraction("126508", "오죽헌");

        List<UnmatchedPlaceName> names = collector().collect().names();

        assertThat(names).extracting(UnmatchedPlaceName::sourceName)
                .containsExactly("경포해수욕장");
        assertThat(names).allSatisfy(name -> {
            assertThat(name.lawdCode()).isEqualTo(GANGNEUNG);
            assertThat(name.regionName()).isEqualTo("강릉시");
        });

        // 받은 응답은 캐시에 남아야 한다. 읽기 전용으로 묶으면 여기서 통째로 실패한다.
        assertThat(cacheRepository.count()).isPositive();
    }

    @Test
    @DisplayName("같은 기준 관광지가 여러 행에 나와도 한 번만 판정 대상이 된다")
    void collectsEachBaseNameOnce() {
        List<UnmatchedPlaceName> names = collector().collect().names();

        assertThat(names).extracting(UnmatchedPlaceName::sourceName)
                .containsExactly("오죽헌", "경포해수욕장");
    }

    private static String emptyBody() {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},\
                "body":{"items":"","numOfRows":1000,"pageNo":1,"totalCount":0}}}""";
    }

    /** 같은 기준 관광지가 연관 장소 수만큼 반복해서 나오는, 공급자 응답 그대로의 모양. */
    private static String body() {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},\
                "body":{"items":{"item":[%s,%s,%s]},"numOfRows":1000,"pageNo":1,"totalCount":3}}}"""
                .formatted(row("오죽헌", "정동진"), row("경포해수욕장", "정동진"),
                        row("경포해수욕장", "초당순두부"));
    }

    private static String row(String baseName, String relatedName) {
        return """
                {"baseYm":"202607","tAtsCd":"base","tAtsNm":"%s","areaCd":"51",\
                "areaNm":"강원특별자치도","signguCd":"51150","signguNm":"강릉시",\
                "rlteTatsCd":"related","rlteTatsNm":"%s","rlteRegnCd":"51",\
                "rlteRegnNm":"강원특별자치도","rlteSignguCd":"51150","rlteSignguNm":"강릉시",\
                "rlteCtgryLclsNm":"관광지","rlteCtgryMclsNm":"중분류","rlteCtgrySclsNm":"소분류",\
                "rlteRank":"1"}""".formatted(baseName, relatedName);
    }
}
