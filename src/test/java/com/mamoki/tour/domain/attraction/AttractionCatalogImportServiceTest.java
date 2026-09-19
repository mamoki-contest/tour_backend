package com.mamoki.tour.domain.attraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportResult;
import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportService;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.infra.korservice.KorServiceClient;
import com.mamoki.tour.infra.korservice.KorServiceProperties;

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

    @MockitoBean
    private KorServiceClient korServiceClient;

    private String fixture;

    @BeforeEach
    void setUp() throws Exception {
        attractionRepository.deleteAllInBatch();

        try (InputStream in = getClass().getResourceAsStream(
                "/fixtures/korservice-areaBasedList2.json")) {
            fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 첫 페이지만 내용을 주고 다음 페이지는 빈 응답을 준다. 공급자가 마지막 뒤로 주는 모양이다.
        given(korServiceClient.areaBasedListByLawdJson(anyString(), any(), any(), anyInt(), anyInt()))
                .willAnswer(invocation -> (int) invocation.getArgument(3) == 1 ? fixture : empty());
        given(korServiceClient.parse(anyString(), anyString()))
                .willAnswer(invocation -> new KorServiceClient(properties(), RestClient.builder())
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

    @Test
    @DisplayName("공급자가 아무것도 주지 않으면 적재하지 않는다")
    void rejectsEmptyProviderResponse() {
        given(korServiceClient.areaBasedListByLawdJson(anyString(), any(), any(), anyInt(), anyInt()))
                .willReturn(empty());

        assertThatThrownBy(() -> importService.importAll())
                .isInstanceOf(IllegalStateException.class);

        assertThat(attractionRepository.count()).isZero();
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
}
