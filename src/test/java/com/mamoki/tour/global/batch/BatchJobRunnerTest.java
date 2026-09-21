package com.mamoki.tour.global.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import com.mamoki.tour.domain.placeimage.importer.PlaceImageJobRequest;
import com.mamoki.tour.domain.placeimage.importer.PlaceImageJobService;
import com.mamoki.tour.domain.placeimage.importer.PlaceImageResult;
import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.placemapping.importer.PlaceMappingJobService;
import com.mamoki.tour.domain.placemapping.importer.PlaceMappingResult;
import com.mamoki.tour.domain.placemapping.importer.SourceMatchRate;
import com.mamoki.tour.domain.tmaprank.importer.TmapRankImportService;
import com.mamoki.tour.domain.visitorstats.importer.VisitorStatsImportService;

/**
 * 커맨드라인 작업 실행기.
 *
 * <p>가장 중요한 것은 인자 없이 기동했을 때 아무것도 실행되지 않는 것이다. 이 조건이 깨지면
 * 앱을 띄울 때마다 외부 API 를 소진하고 스냅샷이 갈린다.
 *
 * <p>두 번째는 <b>작업 결과가 종료 코드로 남는 것</b>이다(#95). 예전에는 "작업 실패가 곧
 * 기동 실패는 아니다" 라는 규칙만 있어서, 실패해도 프로세스가 웹 서버로 남고 배포
 * 스크립트는 성공과 실패를 가릴 수 없었다. 지금은 예외를 밖으로 던지지 않는다는 규칙은
 * 그대로 두고(스택 트레이스가 로그를 덮지 않게), 결과를 종료 코드로 알린다.
 *
 * <table>
 *   <caption>종료 코드</caption>
 *   <tr><td>0</td><td>작업 성공</td></tr>
 *   <tr><td>1</td><td>작업 실패</td></tr>
 *   <tr><td>2</td><td>알 수 없는 작업 이름</td></tr>
 * </table>
 */
class BatchJobRunnerTest {

    private AttractionCatalogImportService catalogImportService;
    private OnlineMentionCollector mentionCollector;
    private TmapRankImportService tmapRankImportService;
    private VisitorStatsImportService visitorStatsImportService;
    private ParkingCatalogImportService parkingCatalogImportService;
    private PlaceMappingJobService placeMappingJobService;
    private PlaceImageJobService placeImageJobService;
    private BatchJobRunner runner;

    @BeforeEach
    void setUp() {
        catalogImportService = Mockito.mock(AttractionCatalogImportService.class);
        mentionCollector = Mockito.mock(OnlineMentionCollector.class);
        tmapRankImportService = Mockito.mock(TmapRankImportService.class);
        visitorStatsImportService = Mockito.mock(VisitorStatsImportService.class);
        parkingCatalogImportService = Mockito.mock(ParkingCatalogImportService.class);
        placeMappingJobService = Mockito.mock(PlaceMappingJobService.class);
        given(placeMappingJobService.run(any()))
                .willAnswer(call -> PlaceMappingResult.notRun(call.getArgument(0), null));

        placeImageJobService = Mockito.mock(PlaceImageJobService.class);
        given(placeImageJobService.run(any())).willReturn(PlaceImageResult.notRun(null));

        given(catalogImportService.importAll())
                .willReturn(new AttractionCatalogImportResult(10, 10, 0, 0));
        given(mentionCollector.collect(any()))
                .willReturn(Mockito.mock(OnlineMentionCollectResult.class, Mockito.RETURNS_DEEP_STUBS));

        runner = new BatchJobRunner(catalogImportService, mentionCollector,
                tmapRankImportService, visitorStatsImportService, parkingCatalogImportService,
                placeMappingJobService, placeImageJobService);
    }

    /** @return 이 실행이 남긴 종료 코드 */
    private int run(String... args) {
        runner.run(new DefaultApplicationArguments(args));

        return runner.getExitCode();
    }

    private void verifyNothingRan() {
        Mockito.verifyNoInteractions(mentionCollector, tmapRankImportService,
                visitorStatsImportService, parkingCatalogImportService, placeMappingJobService,
                placeImageJobService);
        Mockito.verify(catalogImportService, Mockito.never()).importAll();
    }

    @Test
    @DisplayName("인자가 없으면 아무 작업도 실행하지 않는다")
    void runsNothingWithoutJobOption() {
        assertThat(run()).isZero();

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
    @DisplayName("알 수 없는 작업 이름이면 실행하지 않고 종료 코드 2 로 알린다")
    void rejectsUnknownJob() {
        // 오타는 실패와 다르다. 돌릴 작업 자체를 못 찾은 것이라 다시 돌려 봐야 같은 답이다.
        assertThat(run("--job=드롭테이블")).isEqualTo(2);

        verifyNothingRan();
    }

    @Test
    @DisplayName("카탈로그 작업을 실행한다")
    void runsCatalogJob() {
        assertThat(run("--job=catalog")).isZero();

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
    @DisplayName("필요한 인자가 빠지면 실행하지 않고 종료 코드 1 로 알린다")
    void rejectsMissingRequiredOption() {
        assertThat(run("--job=tmap")).isEqualTo(1);
        assertThat(run("--job=visitor-stats")).isEqualTo(1);
        assertThat(run("--job=parking-catalog")).isEqualTo(1);

        Mockito.verifyNoInteractions(tmapRankImportService, visitorStatsImportService,
                parkingCatalogImportService);
    }

    @Test
    @DisplayName("작업이 실패하면 예외를 던지지 않고 종료 코드 1 로 알린다")
    void reportsFailureAsExitCode() {
        // 예외를 그대로 띄우면 스택 트레이스가 기동 로그를 덮어 무엇이 실패했는지가 묻힌다.
        // 실패한 사실은 로그와 종료 코드로 남긴다.
        willThrow(new IllegalStateException("공급자 장애")).given(catalogImportService).importAll();

        assertThatCode(() -> assertThat(run("--job=catalog")).isEqualTo(1))
                .doesNotThrowAnyException();

        Mockito.verify(catalogImportService).importAll();
    }

    @Test
    @DisplayName("실패한 뒤 성공하면 종료 코드도 그 결과를 따른다")
    void exitCodeFollowsTheLastResult() {
        willThrow(new IllegalStateException("공급자 장애")).given(catalogImportService).importAll();
        assertThat(run("--job=catalog")).isEqualTo(1);

        Mockito.reset(catalogImportService);
        placeImageJobService = Mockito.mock(PlaceImageJobService.class);
        given(placeImageJobService.run(any())).willReturn(PlaceImageResult.notRun(null));

        given(catalogImportService.importAll())
                .willReturn(new AttractionCatalogImportResult(10, 10, 0, 0));

        assertThat(run("--job=catalog")).isZero();
    }

    @Test
    @DisplayName("작업 이름은 대소문자를 가리지 않는다")
    void acceptsJobNameCaseInsensitively() {
        run("--job=CATALOG");

        Mockito.verify(catalogImportService).importAll();
    }

    @Test
    @DisplayName("장소 매핑 작업은 원천을 주지 않으면 셋을 모두 돈다")
    void runsPlaceMappingForEverySource() {
        run("--job=place-mapping");

        for (MappingSource source : MappingSource.values()) {
            Mockito.verify(placeMappingJobService).run(source);
        }
    }

    @Test
    @DisplayName("장소 매핑 작업은 지정한 원천만 돈다")
    void runsPlaceMappingForGivenSource() {
        run("--job=place-mapping", "--source=tmap");

        Mockito.verify(placeMappingJobService).run(MappingSource.TMAP);
        Mockito.verify(placeMappingJobService, Mockito.never()).run(MappingSource.VISITOR_STATS);
        Mockito.verify(placeMappingJobService, Mockito.never()).run(MappingSource.RELATED_PLACE);
    }

    @Test
    @DisplayName("알 수 없는 원천이면 아무 원천도 돌지 않는다")
    void rejectsUnknownSource() {
        // 작업 이름은 맞고 인자가 틀렸다. 작업을 돌리다 실패한 것과 같은 자리다.
        assertThat(run("--job=place-mapping", "--source=드롭테이블")).isEqualTo(1);

        Mockito.verifyNoInteractions(placeMappingJobService);
    }

    @Test
    @DisplayName("한 원천이 중간에 멈추면 다음 원천으로 넘어가지 않는다")
    void stopsAfterAnInterruptedSource() {
        // 인증 실패나 한도 초과는 다음 원천에서도 같은 답을 받는다. 계속 부르면 한도만 깎고,
        // 무엇이 왜 멈췄는지가 뒤 원천의 로그에 묻힌다.
        given(placeMappingJobService.run(MappingSource.TMAP))
                .willReturn(new PlaceMappingResult(MappingSource.TMAP, 10, 0, 3, 1, 1, 1,
                        0, 0, 1, new SourceMatchRate(20, 11, 0), "카카오 호출 한도 초과"));

        run("--job=place-mapping");

        Mockito.verify(placeMappingJobService).run(MappingSource.TMAP);
        Mockito.verify(placeMappingJobService, Mockito.never()).run(MappingSource.VISITOR_STATS);
    }

    @Test
    @DisplayName("대표 사진 보강 작업은 인자를 주지 않으면 전체를 새로 수집만 한다")
    void runsPlaceImageJob() {
        assertThat(run("--job=place-image")).isZero();

        Mockito.verify(placeImageJobService).run(PlaceImageJobRequest.of(false, null));
    }

    @Test
    @DisplayName("--refresh 는 값 없이 주어도 재수집이다")
    void runsPlaceImageJobWithRefreshFlag() {
        run("--job=place-image", "--refresh");

        Mockito.verify(placeImageJobService).run(PlaceImageJobRequest.of(true, null));
    }

    @Test
    @DisplayName("--refresh=false 는 재수집하지 않는다는 뜻이다")
    void honoursExplicitRefreshValue() {
        run("--job=place-image", "--refresh=false");

        Mockito.verify(placeImageJobService).run(PlaceImageJobRequest.of(false, null));
    }

    @Test
    @DisplayName("--sigungu 를 주면 그 시·군만 본다")
    void runsPlaceImageJobForOneDistrict() {
        run("--job=place-image", "--sigungu=51150");

        Mockito.verify(placeImageJobService).run(PlaceImageJobRequest.of(false, "51150"));
    }

    @Test
    @DisplayName("작업 목록에 일곱 가지가 모두 들어 있다")
    void listsEveryJob() {
        assertThat(BatchJob.values()).hasSize(7);
        assertThat(BatchJob.names())
                .contains("catalog", "mention", "tmap", "visitor-stats", "parking-catalog",
                        "place-mapping", "place-image");
    }
}
