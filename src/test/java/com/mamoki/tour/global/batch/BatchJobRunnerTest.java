package com.mamoki.tour.global.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;

import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportResult;
import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportService;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollectResult;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollector;
import com.mamoki.tour.domain.parking.importer.ParkingCatalogImportService;
import com.mamoki.tour.domain.tmaprank.importer.TmapRankImportService;
import com.mamoki.tour.domain.visitorstats.importer.VisitorStatsImportService;

/**
 * 커맨드라인 작업 실행기.
 *
 * <p>가장 중요한 것은 인자 없이 기동했을 때 아무것도 실행되지 않는 것이다. 이 조건이 깨지면
 * 앱을 띄울 때마다 외부 API 를 소진하고 스냅샷이 갈린다.
 */
class BatchJobRunnerTest {

    private AttractionCatalogImportService catalogImportService;
    private OnlineMentionCollector mentionCollector;
    private TmapRankImportService tmapRankImportService;
    private VisitorStatsImportService visitorStatsImportService;
    private ParkingCatalogImportService parkingCatalogImportService;
    private BatchJobRunner runner;

    @BeforeEach
    void setUp() {
        catalogImportService = Mockito.mock(AttractionCatalogImportService.class);
        mentionCollector = Mockito.mock(OnlineMentionCollector.class);
        tmapRankImportService = Mockito.mock(TmapRankImportService.class);
        visitorStatsImportService = Mockito.mock(VisitorStatsImportService.class);
        parkingCatalogImportService = Mockito.mock(ParkingCatalogImportService.class);

        given(catalogImportService.importAll())
                .willReturn(new AttractionCatalogImportResult(10, 10, 0, 0));
        given(mentionCollector.collect(any()))
                .willReturn(Mockito.mock(OnlineMentionCollectResult.class, Mockito.RETURNS_DEEP_STUBS));

        runner = new BatchJobRunner(catalogImportService, mentionCollector,
                tmapRankImportService, visitorStatsImportService, parkingCatalogImportService);
    }

    private void run(String... args) {
        runner.run(new DefaultApplicationArguments(args));
    }

    private void verifyNothingRan() {
        Mockito.verifyNoInteractions(mentionCollector, tmapRankImportService,
                visitorStatsImportService, parkingCatalogImportService);
        Mockito.verify(catalogImportService, Mockito.never()).importAll();
    }

    @Test
    @DisplayName("인자가 없으면 아무 작업도 실행하지 않는다")
    void runsNothingWithoutJobOption() {
        run();

        verifyNothingRan();
    }

    @Test
    @DisplayName("다른 인자만 있어도 작업을 실행하지 않는다")
    void runsNothingWithUnrelatedArguments() {
        run("--spring.profiles.active=prod", "--server.port=8080");

        verifyNothingRan();
    }

    @Test
    @DisplayName("빈 job 값은 주지 않은 것으로 본다")
    void treatsBlankJobAsAbsent() {
        run("--job=");

        verifyNothingRan();
    }

    @Test
    @DisplayName("알 수 없는 작업 이름이면 실행하지 않는다")
    void rejectsUnknownJob() {
        run("--job=드롭테이블");

        verifyNothingRan();
    }

    @Test
    @DisplayName("카탈로그 작업을 실행한다")
    void runsCatalogJob() {
        run("--job=catalog");

        Mockito.verify(catalogImportService).importAll();
    }

    @Test
    @DisplayName("언급량 작업은 지정한 달로 수집한다")
    void runsMentionJobWithGivenMonth() {
        run("--job=mention", "--month=202609");

        Mockito.verify(mentionCollector).collect(YearMonth.of(2026, 9));
    }

    @Test
    @DisplayName("달을 주지 않으면 이번 달로 수집한다")
    void runsMentionJobWithCurrentMonth() {
        run("--job=mention");

        Mockito.verify(mentionCollector).collect(YearMonth.now());
    }

    @Test
    @DisplayName("TMAP 작업은 디렉터리와 내려받은 날을 넘긴다")
    void runsTmapJob() {
        run("--job=tmap", "--dir=sample", "--downloaded-on=2026-09-06");

        Mockito.verify(tmapRankImportService)
                .importFrom(Path.of("sample"), LocalDate.of(2026, 9, 6));
    }

    @Test
    @DisplayName("입장객 작업은 파일과 내려받은 날을 넘긴다")
    void runsVisitorStatsJob() {
        run("--job=visitor-stats", "--file=sample/a.xls", "--downloaded-on=2026-09-18");

        Mockito.verify(visitorStatsImportService)
                .importFrom(Path.of("sample/a.xls"), LocalDate.of(2026, 9, 18));
    }

    @Test
    @DisplayName("주차장 표준데이터 작업은 파일과 내려받은 날을 넘긴다")
    void runsParkingCatalogJob() {
        run("--job=parking-catalog", "--file=sample/parking.csv", "--downloaded-on=2026-09-19");

        Mockito.verify(parkingCatalogImportService)
                .importFrom(Path.of("sample/parking.csv"), LocalDate.of(2026, 9, 19));
    }

    @Test
    @DisplayName("필요한 인자가 빠지면 실행하지 않는다")
    void rejectsMissingRequiredOption() {
        run("--job=tmap");
        run("--job=visitor-stats");
        run("--job=parking-catalog");

        Mockito.verifyNoInteractions(tmapRankImportService, visitorStatsImportService,
                parkingCatalogImportService);
    }

    @Test
    @DisplayName("작업이 실패해도 기동을 막지 않는다")
    void doesNotFailStartupWhenJobFails() {
        willThrow(new IllegalStateException("공급자 장애")).given(catalogImportService).importAll();

        run("--job=catalog");

        Mockito.verify(catalogImportService).importAll();
    }

    @Test
    @DisplayName("작업 이름은 대소문자를 가리지 않는다")
    void acceptsJobNameCaseInsensitively() {
        run("--job=CATALOG");

        Mockito.verify(catalogImportService).importAll();
    }

    @Test
    @DisplayName("작업 목록에 다섯 가지가 모두 들어 있다")
    void listsEveryJob() {
        assertThat(BatchJob.values()).hasSize(5);
        assertThat(BatchJob.names())
                .contains("catalog", "mention", "tmap", "visitor-stats", "parking-catalog");
    }
}
