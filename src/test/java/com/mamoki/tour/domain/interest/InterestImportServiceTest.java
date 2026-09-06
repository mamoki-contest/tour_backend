package com.mamoki.tour.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.interest.entity.AttractionInterest;
import com.mamoki.tour.domain.interest.importer.InterestImportException;
import com.mamoki.tour.domain.interest.importer.InterestImportResult;
import com.mamoki.tour.domain.interest.importer.InterestImportService;
import com.mamoki.tour.domain.interest.repository.AttractionInterestRepository;
import com.mamoki.tour.domain.interest.repository.InterestSnapshotRepository;
import com.mamoki.tour.domain.interest.service.InterestSnapshotService;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.enums.InterestMatchStatus;
import com.mamoki.tour.global.enums.SnapshotStatus;

@SpringBootTest
@ActiveProfiles("test")
class InterestImportServiceTest {

    private static final Path SAMPLE_DIR = Path.of("sample");
    private static final LocalDate DOWNLOADED_ON = LocalDate.of(2026, 9, 6);

    @Autowired
    private InterestImportService importService;

    @Autowired
    private InterestSnapshotService snapshotService;

    @Autowired
    private InterestSnapshotRepository snapshotRepository;

    @Autowired
    private AttractionInterestRepository interestRepository;

    @Autowired
    private AttractionRepository attractionRepository;

    @Autowired
    private RegionCodeRepository regionCodeRepository;

    /**
     * 적재는 실제 커밋을 남기므로 테스트 사이에 남는다. 각 테스트가 서로에게 영향을 주지
     * 않도록 앞에서 비운다. 지역코드 시드는 data.sql 이 유지하므로 건드리지 않는다.
     */
    @BeforeEach
    void clearPreviousImports() {
        interestRepository.deleteAllInBatch();
        snapshotRepository.deleteAllInBatch();
        attractionRepository.deleteAllInBatch();
    }

    private Long activeSnapshotId() {
        return snapshotService.findActive().orElseThrow().getId();
    }

    @Test
    @DisplayName("실제 18개 시·군 zip 을 하나의 스냅샷으로 적재하고 활성화한다")
    void importsAllRegionsIntoOneSnapshot() {
        InterestImportResult result = importService.importFrom(SAMPLE_DIR, DOWNLOADED_ON);

        assertThat(result.regionCount()).isEqualTo(18);
        assertThat(result.totalRows()).isEqualTo(18 * 30);
        assertThat(result.snapshot().getStatus()).isEqualTo(SnapshotStatus.ACTIVE);
        assertThat(result.snapshot().getSourcePeriod()).isEqualTo("202508-202607");
        assertThat(result.snapshot().getDownloadedOn()).isEqualTo(DOWNLOADED_ON);
        assertThat(activeSnapshotId()).isEqualTo(result.snapshot().getId());
    }

    @Test
    @DisplayName("원본 지역명과 관광지명을 그대로 보존한다")
    void keepsRawValues() {
        InterestImportResult result = importService.importFrom(SAMPLE_DIR, DOWNLOADED_ON);

        List<AttractionInterest> rows = interestRepository.findAll().stream()
                .filter(row -> row.getSnapshot().getId().equals(result.snapshot().getId()))
                .toList();

        assertThat(rows).anySatisfy(row -> {
            assertThat(row.getRawRegionName()).isEqualTo("강릉시");
            assertThat(row.getRawPlaceName()).isEqualTo("경포해변");
            assertThat(row.getInterestValue()).isEqualByComparingTo(new BigDecimal("14.6"));
            assertThat(row.getSourceRank()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("카탈로그에 있는 관광지는 표준 식별자로 이어진다")
    void matchesAgainstCatalog() {
        RegionCode gangneung = regionCodeRepository.findByLawdCode("51150").orElseThrow();
        attractionRepository.save(Attraction.builder()
                .contentId("126508")
                .name("경포 해변")
                .regionCode(gangneung)
                .dataStatus(DataStatus.AVAILABLE)
                .baseAt(LocalDateTime.now())
                .source("KorService2")
                .build());

        InterestImportResult result = importService.importFrom(SAMPLE_DIR, DOWNLOADED_ON);

        assertThat(result.matchedRows()).isPositive();
        assertThat(interestRepository.findAll())
                .filteredOn(row -> "경포해변".equals(row.getRawPlaceName()))
                .anySatisfy(row -> {
                    assertThat(row.getContentId()).isEqualTo("126508");
                    assertThat(row.getMatchStatus()).isEqualTo(InterestMatchStatus.MATCHED);
                    assertThat(row.isUsableForSorting()).isTrue();
                });
    }

    @Test
    @DisplayName("카탈로그에 없는 장소는 매칭하지 않고 정렬에도 쓰지 않는다")
    void leavesUnmatchedRowsOut() {
        InterestImportResult result = importService.importFrom(SAMPLE_DIR, DOWNLOADED_ON);

        assertThat(result.unmatchedRows()).isPositive();
        assertThat(interestRepository.findAll())
                .filteredOn(row -> row.getMatchStatus() == InterestMatchStatus.UNMATCHED)
                .allSatisfy(row -> {
                    assertThat(row.getContentId()).isNull();
                    assertThat(row.isUsableForSorting()).isFalse();
                });
    }

    @Test
    @DisplayName("시·군이 빠지면 적재를 거부하고 직전 정상 스냅샷을 유지한다")
    void rejectsMissingRegionAndKeepsPreviousSnapshot(@TempDir Path partialDir) throws IOException {
        InterestImportResult previous = importService.importFrom(SAMPLE_DIR, DOWNLOADED_ON);
        copyZips(partialDir, 17);

        assertThatThrownBy(() -> importService.importFrom(partialDir, DOWNLOADED_ON))
                .isInstanceOf(InterestImportException.class)
                .hasMessageContaining("시·군 파일이 빠졌습니다");

        assertThat(activeSnapshotId()).isEqualTo(previous.snapshot().getId());
        assertThat(snapshotRepository.findAll())
                .filteredOn(snapshot -> snapshot.getStatus() == SnapshotStatus.FAILED)
                .isNotEmpty();
    }

    @Test
    @DisplayName("실패 사유가 적재 이력에 남는다")
    void recordsFailureReason(@TempDir Path emptyDir) {
        assertThatThrownBy(() -> importService.importFrom(emptyDir, DOWNLOADED_ON))
                .isInstanceOf(InterestImportException.class)
                .hasMessageContaining("적재할 zip");

        assertThat(snapshotRepository.findAll())
                .filteredOn(snapshot -> snapshot.getStatus() == SnapshotStatus.FAILED)
                .anySatisfy(snapshot ->
                        assertThat(snapshot.getFailureReason()).contains("적재할 zip"));
    }

    @Test
    @DisplayName("두 번 적재하면 직전 스냅샷은 물러나고 활성은 하나만 남는다")
    void replacesActiveSnapshotOnReimport() {
        InterestImportResult first = importService.importFrom(SAMPLE_DIR, DOWNLOADED_ON);
        InterestImportResult second = importService.importFrom(SAMPLE_DIR, DOWNLOADED_ON);

        assertThat(snapshotRepository.findByVersion(first.snapshot().getVersion()))
                .get()
                .extracting(snapshot -> snapshot.getStatus())
                .isEqualTo(SnapshotStatus.SUPERSEDED);
        assertThat(activeSnapshotId()).isEqualTo(second.snapshot().getId());
        assertThat(second.snapshot().getVersion()).isNotEqualTo(first.snapshot().getVersion());
    }

    private void copyZips(Path target, int count) throws IOException {
        try (var files = Files.list(SAMPLE_DIR)) {
            List<Path> zips = files
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .sorted()
                    .limit(count)
                    .toList();

            for (Path zip : zips) {
                Files.copy(zip, target.resolve(zip.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
