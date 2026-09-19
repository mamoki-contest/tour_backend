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
import org.springframework.stereotype.Component;

import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportResult;
import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportService;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollectResult;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollector;
import com.mamoki.tour.domain.parking.importer.ParkingCatalogImportResult;
import com.mamoki.tour.domain.parking.importer.ParkingCatalogImportService;
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
 * </pre>
 *
 * <p><b>{@code --job} 이 없으면 아무것도 하지 않는다.</b> 평소 서버 기동에 영향을 주지 않아야
 * 한다. 이 조건이 깨지면 앱을 띄울 때마다 외부 API 를 소진하게 된다.
 *
 * <p>관리 API 대신 커맨드라인을 쓴다. 이 서비스에는 인증 체계가 없다. PRD 가 회원가입과
 * 서버 계정을 범위 밖으로 두었기 때문이다. 관리 API 를 열면 누구나 호출해 외부 API 를
 * 소진시키거나 스냅샷을 갈아치울 수 있다.
 */
@Component
public class BatchJobRunner implements ApplicationRunner {

    /**
     * 무엇을 돌릴지 고르는 인자 이름.
     *
     * <p>스케줄러도 이 이름으로 배치 실행 여부를 가린다. 두 곳이 다른 이름을 보면 배치
     * 프로세스에서 스케줄러가 함께 뜬다.
     */
    public static final String JOB_OPTION = "job";

    private static final String MONTH_OPTION = "month";
    private static final String DIR_OPTION = "dir";
    private static final String FILE_OPTION = "file";
    private static final String DOWNLOADED_ON_OPTION = "downloaded-on";
    private static final String SOURCE_OPTION = "source";

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private static final Logger log = LoggerFactory.getLogger(BatchJobRunner.class);

    private final AttractionCatalogImportService catalogImportService;
    private final OnlineMentionCollector mentionCollector;
    private final TmapRankImportService tmapRankImportService;
    private final VisitorStatsImportService visitorStatsImportService;
    private final ParkingCatalogImportService parkingCatalogImportService;
    private final PlaceMappingJobService placeMappingJobService;

    public BatchJobRunner(AttractionCatalogImportService catalogImportService,
                          OnlineMentionCollector mentionCollector,
                          TmapRankImportService tmapRankImportService,
                          VisitorStatsImportService visitorStatsImportService,
                          ParkingCatalogImportService parkingCatalogImportService,
                          PlaceMappingJobService placeMappingJobService) {
        this.catalogImportService = catalogImportService;
        this.mentionCollector = mentionCollector;
        this.tmapRankImportService = tmapRankImportService;
        this.visitorStatsImportService = visitorStatsImportService;
        this.parkingCatalogImportService = parkingCatalogImportService;
        this.placeMappingJobService = placeMappingJobService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Optional<String> jobName = option(args, JOB_OPTION);

        if (jobName.isEmpty()) {
            return;
        }

        BatchJob job = BatchJob.from(jobName.get()).orElse(null);

        if (job == null) {
            log.error("알 수 없는 작업입니다: {}. 사용할 수 있는 작업: {}", jobName.get(), BatchJob.names());
            return;
        }

        log.info("작업을 시작합니다: {} ({})", job.jobName(), job.description());

        try {
            run(job, args);
        } catch (RuntimeException e) {
            // 작업 실패가 곧 기동 실패는 아니다. 무엇이 실패했는지 남기고 종료 코드는 건드리지 않는다.
            log.error("작업이 실패했습니다: {}", job.jobName(), e);
        }
    }

    private void run(BatchJob job, ApplicationArguments args) {
        switch (job) {
            case CATALOG -> runCatalog();
            case MENTION -> runMention(args);
            case TMAP -> runTmap(args);
            case VISITOR_STATS -> runVisitorStats(args);
            case PARKING_CATALOG -> runParkingCatalog(args);
            case PLACE_MAPPING -> runPlaceMapping(args);
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
