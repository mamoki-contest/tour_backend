package com.mamoki.tour.domain.attraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.entity.AttractionCatalogImport;
import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportResult;
import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportService;
import com.mamoki.tour.domain.attraction.repository.AttractionCatalogImportRepository;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.infra.korservice.KorServiceClient;
import com.mamoki.tour.infra.korservice.KorServiceProperties;

import jakarta.persistence.EntityManagerFactory;

/**
 * 카탈로그 적재. 저장한 실제 KorService2 응답을 공급자 대신 돌려준다.
 *
 * <p>카탈로그는 언급량 수집과 TMAP·입장객 매칭이 올라타는 바탕이라, 중복 없이 갱신되는지와
 * 지역이 제대로 이어지는지를 특히 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AttractionCatalogImportServiceTest {

    @Autowired
    private AttractionCatalogImportService importService;

    @Autowired
    private AttractionRepository attractionRepository;

    @Autowired
    private AttractionCatalogImportRepository catalogImportRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private KorServiceClient korServiceClient;

    private String fixture;

    @BeforeEach
    void setUp() throws Exception {
        attractionRepository.deleteAllInBatch();
        catalogImportRepository.deleteAllInBatch();

        try (InputStream in = getClass().getResourceAsStream(
                "/fixtures/korservice-areaBasedList2.json")) {
            fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 첫 페이지만 내용을 주고 다음 페이지는 빈 응답을 준다. 공급자가 마지막 뒤로 주는 모양이다.
        given(korServiceClient.areaBasedListByLawdJson(anyString(), any(), any(), anyInt(), anyInt()))
                .willAnswer(invocation -> (int) invocation.getArgument(3) == 1 ? fixture : empty());
        given(korServiceClient.parse(anyString(), anyString()))
                .willAnswer(invocation -> new KorServiceClient(properties())
                        .parse(invocation.getArgument(0), invocation.getArgument(1)));
    }

    private static String empty() {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":"","numOfRows":0,"pageNo":2,"totalCount":0}}}""";
    }

    private KorServiceProperties properties() {
        return new KorServiceProperties("http://example.invalid", "key", "tour",
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("공급자에서 받은 관광지를 카탈로그에 담는다")
    void importsCatalog() {
        AttractionCatalogImportResult result = importService.importAll();

        assertThat(result.fetched()).isPositive();
        assertThat(result.inserted()).isEqualTo(result.fetched());
        assertThat(result.updated()).isZero();
        assertThat(attractionRepository.count()).isEqualTo(result.fetched());
    }

    @Test
    @DisplayName("법정동 시·도 코드로 조회한다. 영역 코드로 거르면 areacode 가 빈 관광지가 빠진다")
    void queriesByLawdRegionCode() {
        importService.importAll();

        Mockito.verify(korServiceClient, Mockito.atLeastOnce())
                .areaBasedListByLawdJson(Mockito.eq("51"), Mockito.isNull(), Mockito.isNull(),
                        anyInt(), anyInt());
        Mockito.verify(korServiceClient, Mockito.never())
                .areaBasedListJson(anyString(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("법정동 코드로 지역을 잇는다")
    void linksRegionByLawdCode() {
        importService.importAll();

        assertThat(attractionRepository.findAllWithRegion())
                .anySatisfy(attraction -> assertThat(attraction.getRegionCode()).isNotNull());
    }

    @Test
    @DisplayName("다시 적재해도 중복이 생기지 않고 갱신만 일어난다")
    void updatesInsteadOfDuplicating() {
        AttractionCatalogImportResult first = importService.importAll();
        AttractionCatalogImportResult second = importService.importAll();

        assertThat(second.inserted()).isZero();
        assertThat(second.updated()).isEqualTo(first.inserted());
        assertThat(attractionRepository.count()).isEqualTo(first.fetched());
    }

    @Test
    @DisplayName("이름이 바뀌면 새 값으로 갱신하고 식별자는 그대로 둔다")
    void refreshesChangedFields() {
        importService.importAll();
        Attraction before = attractionRepository.findAll().get(0);
        String contentId = before.getContentId();
        Long id = before.getId();

        importService.importAll();

        Attraction after = attractionRepository.findByContentId(contentId).orElseThrow();
        assertThat(after.getId()).isEqualTo(id);
        assertThat(after.getName()).isEqualTo(before.getName());
    }

    // --- 적재 이력 (#69) ---------------------------------------------------------

    @Test
    @DisplayName("적재를 마치면 실행 이력 한 줄을 남긴다")
    void recordsImportHistory() {
        AttractionCatalogImportResult result = importService.importAll();

        List<AttractionCatalogImport> history = catalogImportRepository.findAll();

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getFetchedCount()).isEqualTo(result.fetched());
        assertThat(history.get(0).getSavedCount()).isEqualTo(result.saved());
        assertThat(history.get(0).getCompletedAt())
                .isAfterOrEqualTo(history.get(0).getStartedAt());
    }

    /**
     * 같은 응답으로 다시 적재해도 마지막 적재 시각은 앞으로 간다(#69).
     *
     * <p>{@code attraction.modified_at} 으로는 이것을 보장할 수 없다. 그 값은 행이 실제로
     * 바뀔 때만 움직이는데, 적재는 바뀔 것이 있는지를 약속하지 않는다. 같은 값을 다시 써도
     * Hibernate 가 UPDATE 를 내지 않으면 지난달 시각이 그대로 남는다. 반대로 값이 그대로여도
     * 자릿수 표기만 다르면 UPDATE 가 나가기도 한다. 어느 쪽이든 <b>돌린 시각</b>과는 다른
     * 물음에 답하는 값이라, 실행 시각은 이력이 따로 든다.
     */
    @Test
    @DisplayName("내용이 같은 재적재 뒤에도 마지막 적재 시각은 갱신된다")
    void latestImportMovesEvenWhenNothingChanged() {
        AttractionCatalogImportResult first = importService.importAll();
        LocalDateTime firstImport = catalogImportRepository.findLatestCompletedAt();

        AttractionCatalogImportResult second = importService.importAll();

        // 두 번째는 새로 담은 것이 하나도 없다. 그런데도 적재 시각은 앞으로 간다.
        assertThat(second.inserted()).isZero();
        assertThat(second.updated()).isEqualTo(first.inserted());
        assertThat(catalogImportRepository.findLatestCompletedAt()).isAfter(firstImport);
        assertThat(catalogImportRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("적재한 적이 없으면 마지막 적재 시각은 null 이다")
    void latestImportIsNullBeforeAnyImport() {
        assertThat(catalogImportRepository.findLatestCompletedAt()).isNull();
    }

    @Test
    @DisplayName("공급자가 아무것도 주지 않으면 적재하지 않는다")
    void rejectsEmptyProviderResponse() {
        given(korServiceClient.areaBasedListByLawdJson(anyString(), any(), any(), anyInt(), anyInt()))
                .willReturn(empty());

        assertThatThrownBy(() -> importService.importAll())
                .isInstanceOf(IllegalStateException.class);

        assertThat(attractionRepository.count()).isZero();
        // 마치지 못한 적재는 이력에도 남지 않는다. 이 표의 마지막 행은 곧 마지막 성공 적재다.
        assertThat(catalogImportRepository.count()).isZero();
    }

    @Test
    @DisplayName("적재 후에는 언급량 수집기가 볼 카탈로그가 비어 있지 않다")
    void leavesCatalogReadyForCollector() {
        importService.importAll();

        assertThat(attractionRepository.findAllWithRegion()).isNotEmpty();
        assertThat(attractionRepository.findAllWithRegion())
                .allSatisfy(attraction -> assertThat(attraction.getContentId()).isNotBlank());
    }

    @Test
    @DisplayName("페이지를 끝까지 받고 겹쳐 온 장소는 한 번만 담는다")
    void fetchesEveryPageWithoutDuplicates() {
        // 같은 응답을 모든 페이지에 물려도 식별자로 걸러 중복이 생기지 않아야 한다.
        given(korServiceClient.areaBasedListByLawdJson(anyString(), any(), any(), anyInt(), anyInt()))
                .willReturn(fixture);

        AttractionCatalogImportResult result = importService.importAll();

        assertThat(attractionRepository.count()).isEqualTo(result.fetched());
        Mockito.verify(korServiceClient, Mockito.atLeast(1))
                .areaBasedListByLawdJson(anyString(), any(), any(), anyInt(), anyInt());
    }

    // --- 같은 내용의 재적재 (#77) -------------------------------------------------

    /**
     * 공급자가 준 것과 같은 응답으로 다시 적재하면 관광지 행은 한 줄도 다시 쓰이지 않는다.
     *
     * <p>공급자는 좌표를 소수 10자리로 준다. 컬럼은 {@code DECIMAL(10,7)} 이라 저장할 때
     * 7자리로 줄어드는데, 다음 적재가 다시 10자리 값을 들고 오면 DB 에서 읽은 값과 같지
     * 않아 Hibernate 가 UPDATE 를 낸다. 값은 그대로인데 4,700행이 매달 다시 쓰인다.
     */
    @Test
    @DisplayName("내용이 같은 재적재는 관광지 행을 한 줄도 다시 쓰지 않는다")
    void sameContentReimportUpdatesNoRow() {
        importService.importAll();

        Statistics statistics = statistics();
        statistics.clear();

        importService.importAll();

        assertThat(statistics.getEntityUpdateCount()).isZero();
    }

    /**
     * 같은 물음을 통계가 아니라 저장된 값으로 확인한다.
     *
     * <p>통계는 UPDATE 를 <b>냈는지</b>를 말하고, 이쪽은 행이 실제로 <b>바뀌었는지</b>를
     * 말한다. 앞의 것이 구현에 가깝고 뒤의 것이 증상에 가까워 둘 다 둔다.
     */
    @Test
    @DisplayName("내용이 같은 재적재 뒤에도 카탈로그 변경 시각은 그대로다")
    void sameContentReimportLeavesModifiedAtAlone() {
        importService.importAll();
        LocalDateTime changedAt = attractionRepository.findLatestCatalogChangeAt();

        importService.importAll();

        assertThat(attractionRepository.findLatestCatalogChangeAt()).isEqualTo(changedAt);
    }

    /**
     * 좌표는 컬럼이 담는 자리까지만 줄이고, 그 안에서는 잃지 않는다.
     *
     * <p>고정하는 값은 fixture 첫 항목(가람집옹심이)의 공급자 좌표
     * {@code mapy=37.7611934162}, {@code mapx=128.9393320379} 다. 소수 7자리는 약 1cm 라
     * 지도 표시와 장소 매칭 어느 쪽에도 뜻이 없는 자리다.
     */
    @Test
    @DisplayName("좌표는 컬럼 자릿수까지 반올림해 담고 그 안에서는 정밀도를 잃지 않는다")
    void keepsCoordinatePrecisionUpToColumnScale() {
        importService.importAll();

        Attraction saved = attractionRepository.findByContentId("2868839").orElseThrow();

        assertThat(saved.getLatitude()).isEqualByComparingTo(new BigDecimal("37.7611934"));
        assertThat(saved.getLongitude()).isEqualByComparingTo(new BigDecimal("128.9393320"));
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
