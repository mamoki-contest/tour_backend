package com.mamoki.tour.global.batch;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportResult;
import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportService;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollectResult;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollector;
import com.mamoki.tour.domain.parking.importer.ParkingCatalogImportResult;
import com.mamoki.tour.domain.parking.importer.ParkingCatalogImportService;
import com.mamoki.tour.domain.placeimage.importer.PlaceImageJobRequest;
import com.mamoki.tour.domain.placeimage.importer.PlaceImageJobService;
import com.mamoki.tour.domain.placeimage.importer.PlaceImageResult;
import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.placemapping.importer.PlaceMappingJobService;
import com.mamoki.tour.domain.placemapping.importer.PlaceMappingResult;
import com.mamoki.tour.domain.tmaprank.importer.TmapRankImportResult;
import com.mamoki.tour.domain.tmaprank.importer.TmapRankImportService;
import com.mamoki.tour.domain.visitorstats.importer.VisitorStatsImportResult;
import com.mamoki.tour.domain.visitorstats.importer.VisitorStatsImportService;

/**
 * 적재·수집 작업을 커맨드라인으로 실행한다.
 *
 * <pre>
 * java -jar app.jar --job=catalog
 * java -jar app.jar --job=mention --month=202609
 * java -jar app.jar --job=tmap --dir=sample --downloaded-on=2026-09-06
 * java -jar app.jar --job=visitor-stats --file=sample/입장객.xls --downloaded-on=2026-09-18
 * java -jar app.jar --job=parking-catalog --file=sample/전국주차장정보표준데이터.csv --downloaded-on=2026-09-19
 * java -jar app.jar --job=place-mapping --source=tmap
 * java -jar app.jar --job=place-image --refresh --sigungu=51150
 * </pre>
 *
 * <p><b>{@code --job} 이 없으면 아무것도 하지 않는다.</b> 평소 서버 기동에 영향을 주지 않아야
 * 한다. 이 조건이 깨지면 앱을 띄울 때마다 외부 API 를 소진하게 된다.
 *
 * <p>관리 API 대신 커맨드라인을 쓴다. 이 서비스에는 인증 체계가 없다. PRD 가 회원가입과
 * 서버 계정을 범위 밖으로 두었기 때문이다. 관리 API 를 열면 누구나 호출해 외부 API 를
 * 소진시키거나 스냅샷을 갈아치울 수 있다.
 *
 * <p>작업 결과는 <b>종료 코드</b>로 남긴다(#95). 예외를 밖으로 던지지 않는 것은 그대로다 —
 * 스택 트레이스가 기동 로그를 덮으면 무엇이 실패했는지가 오히려 묻힌다. 대신 스크립트가
 * 로그를 읽지 않고도 결과를 알 수 있게 {@link ExitCodeGenerator} 로 알린다.
 *
 * <table>
 *   <caption>종료 코드</caption>
 *   <tr><td>0</td><td>작업 성공(또는 {@code --job} 없는 평범한 기동)</td></tr>
 *   <tr><td>1</td><td>작업 실패</td></tr>
 *   <tr><td>2</td><td>알 수 없는 작업 이름</td></tr>
 * </table>
 */
@Component
public class BatchJobRunner implements ApplicationRunner, ExitCodeGenerator {

    /** 작업 성공. */
    private static final int SUCCESS = 0;

    /** 작업 실패. 같은 명령을 다시 돌려 볼 만한 갈래다. */
    private static final int JOB_FAILED = 1;

    /** 알 수 없는 작업 이름. 다시 돌려도 같은 답이므로 실패와 구분한다. */
    private static final int UNKNOWN_JOB = 2;

    private static final String JOB_OPTION = BatchJobMode.JOB_OPTION;

    private static final String MONTH_OPTION = "month";
    private static final String DIR_OPTION = "dir";
    private static final String FILE_OPTION = "file";
    private static final String DOWNLOADED_ON_OPTION = "downloaded-on";
    private static final String SOURCE_OPTION = "source";
    private static final String REFRESH_OPTION = "refresh";
    private static final String SIGUNGU_OPTION = "sigungu";

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private static final Logger log = LoggerFactory.getLogger(BatchJobRunner.class);

    private final AttractionCatalogImportService catalogImportService;
    private final OnlineMentionCollector mentionCollector;
    private final TmapRankImportService tmapRankImportService;
    private final VisitorStatsImportService visitorStatsImportService;
    private final ParkingCatalogImportService parkingCatalogImportService;
    private final PlaceMappingJobService placeMappingJobService;
    private final PlaceImageJobService placeImageJobService;

    /** 마지막 실행이 남긴 결과. {@code --job} 없이 뜬 프로세스에서는 0 그대로다. */
    private volatile int exitCode = SUCCESS;

    public BatchJobRunner(AttractionCatalogImportService catalogImportService,
                          OnlineMentionCollector mentionCollector,
                          TmapRankImportService tmapRankImportService,
                          VisitorStatsImportService visitorStatsImportService,
                          ParkingCatalogImportService parkingCatalogImportService,
                          PlaceMappingJobService placeMappingJobService,
                          PlaceImageJobService placeImageJobService) {
        this.catalogImportService = catalogImportService;
        this.mentionCollector = mentionCollector;
        this.tmapRankImportService = tmapRankImportService;
        this.visitorStatsImportService = visitorStatsImportService;
        this.parkingCatalogImportService = parkingCatalogImportService;
        this.placeMappingJobService = placeMappingJobService;
        this.placeImageJobService = placeImageJobService;
    }

    @Override
    public void run(ApplicationArguments args) {
        exitCode = SUCCESS;

        Optional<String> jobName = option(args, JOB_OPTION);

        if (jobName.isEmpty()) {
            return;
        }

        BatchJob job = BatchJob.from(jobName.get()).orElse(null);

        if (job == null) {
            log.error("알 수 없는 작업입니다: {}. 사용할 수 있는 작업: {}", jobName.get(), BatchJob.names());
            exitCode = UNKNOWN_JOB;
            return;
        }

        log.info("작업을 시작합니다: {} ({})", job.jobName(), job.description());

        try {
            run(job, args);
        } catch (RuntimeException e) {
            // 예외를 그대로 띄우면 스택 트레이스가 기동 로그를 덮어 무엇이 실패했는지가 묻힌다.
            // 무엇이 실패했는지는 로그에, 실패했다는 사실은 종료 코드에 남긴다.
            log.error("작업이 실패했습니다: {}", job.jobName(), e);
            exitCode = JOB_FAILED;
        }
    }

    /** 마지막 실행의 결과. {@code BatchJobExit} 이 이 값으로 프로세스를 내린다. */
    @Override
    public int getExitCode() {
        return exitCode;
    }

    private void run(BatchJob job, ApplicationArguments args) {
        switch (job) {
            case CATALOG -> runCatalog();
            case MENTION -> runMention(args);
            case TMAP -> runTmap(args);
            case VISITOR_STATS -> runVisitorStats(args);
            case PARKING_CATALOG -> runParkingCatalog(args);
            case PLACE_MAPPING -> runPlaceMapping(args);
            case PLACE_IMAGE -> runPlaceImage(args);
        }
    }

    /**
     * 원천을 주지 않으면 셋을 모두 돈다.
     *
     * <p>한 원천에서 인증 실패나 한도 초과로 멈추면 거기서 끝낸다. 다음 원천으로 넘어가도
     * 같은 답을 받으면서 한도만 깎고, 무엇이 왜 멈췄는지가 뒤 원천의 로그에 묻힌다.
     */
    private void runPlaceMapping(ApplicationArguments args) {
        List<MappingSource> sources = option(args, SOURCE_OPTION)
                .map(value -> List.of(MappingSource.from(value)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "알 수 없는 원천입니다: %s. 사용할 수 있는 값: %s"
                                        .formatted(value, MappingSource.optionValues())))))
                .orElseGet(() -> List.of(MappingSource.values()));

        for (MappingSource source : sources) {
            PlaceMappingResult result = placeMappingJobService.run(source);

            log.info("장소 매핑 결과: source={}, {}", source.optionValue(), result.summary());

            // 분모를 정리하기 전과 후를 항상 함께 찍는다. 정리한 뒤 수치만 남기면 실제로 더
            // 이어서 오른 것인지 분모를 줄여서 오른 것인지 뒤에서 구별할 수 없다.
            log.info("장소 매핑 매칭률: source={}, {}", source.optionValue(), result.matchRate().summary());

            if (result.stoppedEarly()) {
                log.warn("장소 매핑을 끝까지 돌지 못했습니다: {}. 남은 원천은 다음 실행이 봅니다.",
                        result.stoppedReason());
                return;
            }
        }
    }

    /**
     * 사진 없는 관광지에 대표 사진을 찾아 둔다(#99).
     *
     * <p>{@code --refresh} 는 값 없이 준다. 이미 물어본 관광지까지 다시 도는 무거운 갈래라
     * 기본은 꺼짐이다 — 검색어 규칙이나 필터를 바꿔 다른 답을 기대할 때만 켠다.
     *
     * <p>{@code --sigungu} 는 법정동 시·군 코드 5자리다. 한 시·군만 돌려 보고 결과를 확인한
     * 뒤 전체로 넓히는 데 쓴다.
     */
    private void runPlaceImage(ApplicationArguments args) {
        PlaceImageResult result = placeImageJobService.run(PlaceImageJobRequest.of(
                flag(args, REFRESH_OPTION), option(args, SIGUNGU_OPTION).orElse(null)));

        log.info("대표 사진 보강 결과: {}", result.summary());

        if (result.stoppedEarly()) {
            log.warn("대표 사진 보강을 끝까지 돌지 못했습니다: {}. 남은 관광지는 다음 실행이 봅니다.",
                    result.stoppedReason());
        }
    }

    private void runCatalog() {
        AttractionCatalogImportResult result = catalogImportService.importAll();

        log.info("카탈로그 적재 완료: 받음={}, 신규={}, 갱신={}, 지역 미매핑={}",
                result.fetched(), result.inserted(), result.updated(), result.regionUnmapped());
    }

    /** 수집할 달을 주지 않으면 이번 달로 본다. 지난 달을 다시 수집하려면 명시한다. */
    private void runMention(ApplicationArguments args) {
        YearMonth month = option(args, MONTH_OPTION)
                .map(BatchJobRunner::parseMonth)
                .orElseGet(YearMonth::now);

        OnlineMentionCollectResult result = mentionCollector.collect(month);

        log.info("언급량 수집 완료: version={}, 대상={}, 수집={}, 모호={}",
                result.snapshot().getVersion(), result.target(), result.collected(), result.ambiguous());
    }

    private void runTmap(ApplicationArguments args) {
        Path directory = Path.of(required(args, DIR_OPTION,
                "TMAP zip 묶음이 든 디렉터리를 --dir 로 지정하세요."));

        TmapRankImportResult result =
                tmapRankImportService.importFrom(directory, downloadedOn(args));

        log.info("TMAP 적재 완료: version={}, 지역={}, 행={}, 매칭={}",
                result.snapshot().getVersion(), result.regionCount(),
                result.totalRows(), result.matchedRows());
    }

    private void runVisitorStats(ApplicationArguments args) {
        Path file = Path.of(required(args, FILE_OPTION,
                "입장객통계 엑셀 경로를 --file 로 지정하세요."));

        VisitorStatsImportResult result =
                visitorStatsImportService.importFrom(file, downloadedOn(args));

        log.info("입장객통계 적재 완료: version={}, 공표월={}, 행={}, 매칭={}",
                result.snapshot().getVersion(), result.snapshot().getPublishedMonth(),
                result.totalRows(), result.matchedRows());
    }

    private void runParkingCatalog(ApplicationArguments args) {
        Path file = Path.of(required(args, FILE_OPTION,
                "전국주차장정보표준데이터 CSV 경로를 --file 로 지정하세요."));

        ParkingCatalogImportResult result =
                parkingCatalogImportService.importFrom(file, downloadedOn(args));

        log.info("주차장 표준데이터 적재 완료: version={}, 기준일={}, 행={}, 좌표없음={}, 시·군={}",
                result.snapshot().getVersion(), result.snapshot().getDataBaseDate(),
                result.totalRows(), result.withoutCoordinates(), result.districtCount());
    }

    /** 내려받은 날을 주지 않으면 오늘로 본다. 파일을 받은 날과 적재한 날이 같은 경우가 대부분이다. */
    private static LocalDate downloadedOn(ApplicationArguments args) {
        return option(args, DOWNLOADED_ON_OPTION).map(LocalDate::parse).orElseGet(LocalDate::now);
    }

    private static YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value, MONTH);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("수집할 달은 yyyyMM 형식입니다: " + value, e);
        }
    }

    private static String required(ApplicationArguments args, String name, String message) {
        return option(args, name).orElseThrow(() -> new IllegalArgumentException(message));
    }

    /**
     * 값 없이 주는 깃발 인자.
     *
     * <p>{@code --refresh} 처럼 있는 것만으로 뜻이 되는 인자다. 값을 함께 주면
     * ({@code --refresh=false}) 그 값을 따른다 — 스크립트가 변수로 켜고 끄는 자리가 있다.
     */
    private static boolean flag(ApplicationArguments args, String name) {
        if (!args.containsOption(name)) {
            return false;
        }

        return option(args, name).map(Boolean::parseBoolean).orElse(true);
    }

    /** 같은 옵션을 여러 번 주면 첫 값을 쓴다. 빈 값은 주지 않은 것으로 본다. */
    private static Optional<String> option(ApplicationArguments args, String name) {
        if (!args.containsOption(name)) {
            return Optional.empty();
        }

        List<String> values = args.getOptionValues(name);

        return values == null || values.isEmpty() || values.get(0).isBlank()
                ? Optional.empty()
                : Optional.of(values.get(0).strip());
    }
}
