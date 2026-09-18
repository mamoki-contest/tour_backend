package com.mamoki.tour.domain.visitorstats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mamoki.tour.domain.attraction.dto.VisitorStatsView;
import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.attraction.service.SignalLookupService;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.visitorstats.entity.VisitorStatsSnapshot;
import com.mamoki.tour.domain.visitorstats.importer.VisitorStatsImportException;
import com.mamoki.tour.domain.visitorstats.importer.VisitorStatsImportResult;
import com.mamoki.tour.domain.visitorstats.importer.VisitorStatsImportService;
import com.mamoki.tour.domain.visitorstats.repository.VisitorStatsEntryRepository;
import com.mamoki.tour.domain.visitorstats.repository.VisitorStatsSnapshotRepository;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.enums.SnapshotStatus;
import com.mamoki.tour.global.enums.VisitorCountStatus;

/**
 * 실제 공식 파일로 적재부터 조회 응답까지 확인한다.
 *
 * <p>표본은 {@code sample/} 에 둔 2026-09-18 자 강원 전체 파일이다. 공표월은 모든 시·군이
 * 갖춰진 2025-12 다.
 */
@SpringBootTest
@ActiveProfiles("test")
class VisitorStatsImportServiceTest {

    private static final LocalDate DOWNLOADED_ON = LocalDate.of(2026, 9, 18);

    /**
     * 공식 파일은 시트 하나에 강원 전체가 들어 있어 잘라낸 표본을 만들기 어렵다. TMAP zip 과
     * 같이 {@code sample/} 의 원본을 그대로 읽는다. 복사본을 테스트 자원에 또 두면 340KB 짜리
     * 같은 파일이 저장소에 두 번 들어간다.
     */
    private static Path sampleFile() {
        try (java.util.stream.Stream<Path> files = Files.list(Path.of("sample"))) {
            return files.filter(path -> path.getFileName().toString().startsWith("주요관광지점 입장객")
                            && path.getFileName().toString().endsWith(".xls"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "sample 에 입장객통계 파일이 없습니다. README 의 다운로드 조건을 참고하세요."));
        } catch (IOException e) {
            throw new IllegalStateException("sample 디렉터리를 읽지 못했습니다.", e);
        }
    }


    @Autowired
    private VisitorStatsImportService importService;

    @Autowired
    private VisitorStatsSnapshotRepository snapshotRepository;

    @Autowired
    private VisitorStatsEntryRepository entryRepository;

    @Autowired
    private AttractionRepository attractionRepository;

    @Autowired
    private RegionCodeRepository regionCodeRepository;

    @Autowired
    private SignalLookupService signalLookupService;

    @BeforeEach
    void reset() {
        entryRepository.deleteAllInBatch();
        snapshotRepository.deleteAllInBatch();
        attractionRepository.deleteAllInBatch();
    }

    /** 파일의 (시·군, 관광지명) 과 같은 카탈로그 항목을 만든다. */
    private Attraction saveAttraction(String contentId, String name, String lawdCode) {
        RegionCode region = regionCodeRepository.findByLawdCode(lawdCode).orElseThrow();

        return attractionRepository.save(Attraction.builder()
                .contentId(contentId)
                .name(name)
                .regionCode(region)
                .dataStatus(DataStatus.AVAILABLE)
                .baseAt(LocalDateTime.now())
                .source("KorService2")
                .build());
    }

    @Test
    @DisplayName("공식 파일을 적재하고 스냅샷을 활성화한다")
    void importsAndActivates() {
        VisitorStatsImportResult result = importService.importFrom(sampleFile(), DOWNLOADED_ON);

        assertThat(result.snapshot().getStatus()).isEqualTo(SnapshotStatus.ACTIVE);
        assertThat(result.snapshot().getPublishedMonth()).isEqualTo("202512");
        assertThat(result.snapshot().getSourcePeriod()).isEqualTo("202501-202606");
        assertThat(result.totalRows()).isEqualTo(254);
        assertThat(entryRepository.count()).isEqualTo(254);
    }

    @Test
    @DisplayName("카탈로그에 있는 관광지만 식별자에 이어 붙인다")
    void matchesOnlyCatalogPlaces() {
        saveAttraction("1", "강촌레일파크", "51110");

        VisitorStatsImportResult result = importService.importFrom(sampleFile(), DOWNLOADED_ON);

        assertThat(result.matchedRows()).isEqualTo(1);
        assertThat(result.unmatchedRows()).isEqualTo(result.totalRows() - 1);
    }

    @Test
    @DisplayName("시·군이 다르면 이름이 같아도 잇지 않는다")
    void doesNotMatchAcrossRegions() {
        // 강촌레일파크는 파일에서 춘천시(51110) 소속이다. 강릉시로 두면 이어지지 않아야 한다.
        saveAttraction("1", "강촌레일파크", "51150");

        VisitorStatsImportResult result = importService.importFrom(sampleFile(), DOWNLOADED_ON);

        assertThat(result.matchedRows()).isZero();
    }

    @Test
    @DisplayName("공표월이 확정 공표 전이면 잠정으로 남긴다")
    void marksProvisionalBeforeConfirmation() {
        // 2025년 확정치는 2026-04 에 공표된다. 그 전에 적재하면 잠정이다.
        VisitorStatsImportResult result =
                importService.importFrom(sampleFile(), DOWNLOADED_ON, LocalDate.of(2026, 3, 31));

        assertThat(result.snapshot().getCountStatus()).isEqualTo(VisitorCountStatus.PROVISIONAL);
    }

    @Test
    @DisplayName("확정 공표 시점을 지나면 확정으로 남긴다")
    void marksConfirmedAfterPublication() {
        VisitorStatsImportResult result =
                importService.importFrom(sampleFile(), DOWNLOADED_ON, LocalDate.of(2026, 4, 1));

        assertThat(result.snapshot().getCountStatus()).isEqualTo(VisitorCountStatus.CONFIRMED);
    }

    @Test
    @DisplayName("적재된 값이 조회 응답으로 그대로 이어진다")
    void servesImportedValue() {
        saveAttraction("1", "강촌레일파크", "51110");
        importService.importFrom(sampleFile(), DOWNLOADED_ON);

        Optional<java.util.Map<String, VisitorStatsView>> views =
                signalLookupService.findVisitorStats(List.of("1"));

        assertThat(views).isPresent();
        VisitorStatsView view = views.get().get("1");
        assertThat(view.status()).isEqualTo(VisitorStatsView.Status.AVAILABLE);
        assertThat(view.count()).isPositive();
        assertThat(view.period()).isEqualTo("202512");
        assertThat(view.countStatus()).isNotNull();
    }

    @Test
    @DisplayName("적재 전에는 값이 아니라 아직 적재하지 않았다는 사실을 전달한다")
    void reportsNotImportedBeforeImport() {
        assertThat(signalLookupService.findVisitorStats(List.of("1"))).isEmpty();
    }

    @Test
    @DisplayName("공표월에 집계가 없던 관광지는 0 명이 아니라 결과에 담기지 않는다")
    void omitsPlacesWithoutCount() {
        saveAttraction("1", "강촌레일파크", "51110");
        saveAttraction("2", "존재하지않는관광지", "51110");
        importService.importFrom(sampleFile(), DOWNLOADED_ON);

        java.util.Map<String, VisitorStatsView> views =
                signalLookupService.findVisitorStats(List.of("1", "2")).orElseThrow();

        assertThat(views).containsKey("1");
        assertThat(views).doesNotContainKey("2");
    }

    @Test
    @DisplayName("깨진 파일은 적재하지 않고 실패 이력만 남긴다")
    void recordsFailureWithoutActivating(@TempDir Path tempDir) throws IOException {
        Path broken = tempDir.resolve("broken.xls");
        Files.writeString(broken, "not an excel");

        assertThatThrownBy(() -> importService.importFrom(broken, DOWNLOADED_ON))
                .isInstanceOf(VisitorStatsImportException.class);

        assertThat(entryRepository.count()).isZero();
        assertThat(snapshotRepository.findByStatus(SnapshotStatus.ACTIVE)).isEmpty();
        assertThat(snapshotRepository.findAll()).extracting(VisitorStatsSnapshot::getStatus)
                .containsOnly(SnapshotStatus.FAILED);
    }

    @Test
    @DisplayName("다시 적재하면 직전 스냅샷이 물러나고 새 스냅샷만 활성이다")
    void supersedesPreviousSnapshot() {
        importService.importFrom(sampleFile(), DOWNLOADED_ON);
        VisitorStatsImportResult second = importService.importFrom(sampleFile(), DOWNLOADED_ON);

        assertThat(snapshotRepository.findAll()).hasSize(2);
        assertThat(snapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .get()
                .extracting(VisitorStatsSnapshot::getId)
                .isEqualTo(second.snapshot().getId());
    }
}
