package com.mamoki.tour.domain.parking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mamoki.tour.domain.parking.entity.ParkingLot;
import com.mamoki.tour.domain.parking.entity.ParkingLotSnapshot;
import com.mamoki.tour.domain.parking.importer.ParkingCatalogImportException;
import com.mamoki.tour.domain.parking.importer.ParkingCatalogImportResult;
import com.mamoki.tour.domain.parking.importer.ParkingCatalogImportService;
import com.mamoki.tour.domain.parking.repository.ParkingLotRepository;
import com.mamoki.tour.domain.parking.repository.ParkingLotSnapshotRepository;
import com.mamoki.tour.global.enums.SnapshotStatus;

/**
 * 실제 공식 파일로 적재 수명주기를 확인한다.
 *
 * <p>표본은 {@code sample/} 에 둔 2026-09-19 자 전국 파일이다. 전국 18,883행 중 강원
 * 1,398행만 들어와야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ParkingCatalogImportServiceTest {

    private static final LocalDate DOWNLOADED_ON = LocalDate.of(2026, 9, 19);

    /** 강원 행 수. 공급자 파일이 바뀌면 이 값도 함께 확인해야 한다. */
    private static final int GANGWON_ROWS = 1398;

    private static Path sampleFile() {
        return Path.of("sample", "전국주차장정보표준데이터.csv");
    }

    @Autowired
    private ParkingCatalogImportService importService;

    @Autowired
    private ParkingLotSnapshotRepository snapshotRepository;

    @Autowired
    private ParkingLotRepository lotRepository;

    @BeforeEach
    void reset() {
        lotRepository.deleteAllInBatch();
        snapshotRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("공식 파일의 강원 행을 적재하고 스냅샷을 활성화한다")
    void importsGangwonRowsAndActivates() {
        ParkingCatalogImportResult result = importService.importFrom(sampleFile(), DOWNLOADED_ON);

        assertThat(result.snapshot().getStatus()).isEqualTo(SnapshotStatus.ACTIVE);
        assertThat(result.totalRows()).isEqualTo(GANGWON_ROWS);
        assertThat(result.districtCount()).isEqualTo(18);
        assertThat(lotRepository.count()).isEqualTo(GANGWON_ROWS);
        assertThat(result.snapshot().getDownloadedOn()).isEqualTo(DOWNLOADED_ON);
        assertThat(result.snapshot().getDataBaseDate()).isNotNull();
        assertThat(result.snapshot().getImportedAt()).isNotNull();
    }

    @Test
    @DisplayName("강원 밖 시·도는 한 행도 들어오지 않는다")
    void keepsOnlyGangwonProviders() {
        importService.importFrom(sampleFile(), DOWNLOADED_ON);

        assertThat(lotRepository.findAll()).extracting(ParkingLot::getProviderName)
                .allSatisfy(name -> assertThat(name).startsWith("강원특별자치도 "));
    }

    @Test
    @DisplayName("좌표가 없는 행도 적재하되 반경 조회에서는 빠진다")
    void keepsRowsWithoutCoordinates() {
        ParkingCatalogImportResult result = importService.importFrom(sampleFile(), DOWNLOADED_ON);

        assertThat(result.withoutCoordinates()).isEqualTo(4);
        assertThat(result.withCoordinates()).isEqualTo(GANGWON_ROWS - 4);

        List<ParkingLot> withoutCoordinates = lotRepository.findAll().stream()
                .filter(lot -> lot.getLatitude() == null)
                .toList();

        assertThat(withoutCoordinates).hasSize(4);
        // 0 으로 채우지 않는다. 0,0 은 서아프리카 앞바다다.
        assertThat(withoutCoordinates).allSatisfy(lot ->
                assertThat(lot.getLongitude()).isNull());
    }

    @Test
    @DisplayName("한 주차장의 저장 항목이 파일 값 그대로 남는다")
    void storesEveryField() {
        importService.importFrom(sampleFile(), DOWNLOADED_ON);

        ParkingLot lot = lotRepository.findAll().stream()
                .filter(entry -> "252-2-000101".equals(entry.getManagementNumber()))
                .findFirst()
                .orElseThrow();

        assertThat(lot.getName()).startsWith("중앙시장 제1공영주차장");
        assertThat(lot.getCategory()).isEqualTo("공영");
        assertThat(lot.getParkingType()).isEqualTo("노외");
        assertThat(lot.getCapacity()).isEqualTo(375);
        assertThat(lot.getOperatingDays()).isNotBlank();
        assertThat(lot.getWeekdayOpenTime()).isNotBlank();
        assertThat(lot.getFeeInfo()).isNotBlank();
        assertThat(lot.getLatitude()).isNotNull();
        assertThat(lot.getLongitude()).isNotNull();
        assertThat(lot.getDataBaseDate()).isNotNull();
        assertThat(lot.getProviderName()).isEqualTo("강원특별자치도 강릉시");
        assertThat(lot.displayAddress()).contains("강릉시");
    }

    @Test
    @DisplayName("적재한 주차장을 좌표 사각형으로 찾을 수 있다")
    void findsLotsWithinBox() {
        ParkingCatalogImportResult result = importService.importFrom(sampleFile(), DOWNLOADED_ON);

        // 강릉중앙시장 주변 약 ±1km.
        List<ParkingLot> nearby = lotRepository.findWithinBox(result.snapshot(),
                new BigDecimal("37.7438"), new BigDecimal("37.7618"),
                new BigDecimal("128.8853"), new BigDecimal("128.9081"));

        assertThat(nearby).isNotEmpty();
        assertThat(nearby).extracting(ParkingLot::getProviderName)
                .containsOnly("강원특별자치도 강릉시");
        assertThat(nearby).extracting(ParkingLot::getName)
                .anySatisfy(name -> assertThat(name).contains("중앙시장"));
        // 좌표가 없는 행은 사각형에 들어올 수 없다.
        assertThat(nearby).allSatisfy(lot -> assertThat(lot.getLatitude()).isNotNull());
    }

    @Test
    @DisplayName("깨진 파일은 적재하지 않고 실패 이력만 남긴다")
    void recordsFailureWithoutActivating(@TempDir Path tempDir) throws IOException {
        Path broken = tempDir.resolve("broken.csv");
        Files.writeString(broken, "주차장명,주차구획수\n가나다,10");

        assertThatThrownBy(() -> importService.importFrom(broken, DOWNLOADED_ON))
                .isInstanceOf(ParkingCatalogImportException.class);

        assertThat(lotRepository.count()).isZero();
        assertThat(snapshotRepository.findByStatus(SnapshotStatus.ACTIVE)).isEmpty();
        assertThat(snapshotRepository.findAll()).extracting(ParkingLotSnapshot::getStatus)
                .containsOnly(SnapshotStatus.FAILED);
    }

    /**
     * 파일이 없으면 적재를 시작한 적도 없다. 그것까지 FAILED 이력으로 남기면 경로를 잘못 친
     * 실행이 쌓여, 정작 봐야 할 진짜 적재 실패가 그 사이에 묻힌다.
     */
    @Test
    @DisplayName("없는 파일로는 실패 이력조차 남기지 않는다")
    void leavesNoTraceWhenFileIsMissing(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("없는파일.csv");

        assertThatThrownBy(() -> importService.importFrom(missing, DOWNLOADED_ON))
                .isInstanceOf(ParkingCatalogImportException.class);

        assertThat(snapshotRepository.findAll()).isEmpty();
        assertThat(lotRepository.count()).isZero();
    }

    @Test
    @DisplayName("없는 파일을 받아도 직전 스냅샷은 그대로 활성으로 남는다")
    void keepsPreviousSnapshotWhenFileIsMissing(@TempDir Path tempDir) {
        ParkingCatalogImportResult first = importService.importFrom(sampleFile(), DOWNLOADED_ON);

        assertThatThrownBy(() ->
                importService.importFrom(tempDir.resolve("없는파일.csv"), DOWNLOADED_ON.plusDays(1)))
                .isInstanceOf(ParkingCatalogImportException.class);

        assertThat(snapshotRepository.findAll()).hasSize(1);
        assertThat(snapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .get()
                .extracting(ParkingLotSnapshot::getId)
                .isEqualTo(first.snapshot().getId());
    }

    @Test
    @DisplayName("검증에 실패하면 직전 스냅샷이 그대로 활성으로 남는다")
    void keepsPreviousSnapshotWhenValidationFails(@TempDir Path tempDir) throws IOException {
        ParkingCatalogImportResult first = importService.importFrom(sampleFile(), DOWNLOADED_ON);

        Path broken = tempDir.resolve("broken.csv");
        Files.writeString(broken, "깨진,헤더\n1,2");

        assertThatThrownBy(() -> importService.importFrom(broken, DOWNLOADED_ON.plusDays(1)))
                .isInstanceOf(ParkingCatalogImportException.class);

        assertThat(snapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .get()
                .extracting(ParkingLotSnapshot::getId)
                .isEqualTo(first.snapshot().getId());
        assertThat(lotRepository.count()).isEqualTo(GANGWON_ROWS);
    }

    @Test
    @DisplayName("강원 행이 하나도 없는 파일로는 교체하지 않는다")
    void doesNotReplaceWithEmptyGangwonFile(@TempDir Path tempDir) throws IOException {
        ParkingCatalogImportResult first = importService.importFrom(sampleFile(), DOWNLOADED_ON);

        Path other = tempDir.resolve("other-province.csv");
        Files.write(other, onlyOtherProvince().getBytes(Charset.forName("MS949")));

        assertThatThrownBy(() -> importService.importFrom(other, DOWNLOADED_ON.plusDays(1)))
                .isInstanceOf(ParkingCatalogImportException.class);

        assertThat(snapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .get()
                .extracting(ParkingLotSnapshot::getId)
                .isEqualTo(first.snapshot().getId());
    }

    @Test
    @DisplayName("다시 적재하면 직전 스냅샷이 물러나고 새 스냅샷만 활성이다")
    void supersedesPreviousSnapshot() {
        importService.importFrom(sampleFile(), DOWNLOADED_ON);
        ParkingCatalogImportResult second =
                importService.importFrom(sampleFile(), DOWNLOADED_ON.plusDays(1));

        assertThat(snapshotRepository.findAll()).hasSize(2);
        assertThat(snapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .get()
                .extracting(ParkingLotSnapshot::getId)
                .isEqualTo(second.snapshot().getId());
        assertThat(snapshotRepository.findAll()).filteredOn(
                        snapshot -> snapshot.getStatus() == SnapshotStatus.SUPERSEDED)
                .hasSize(1);
    }

    @Test
    @DisplayName("같은 날 두 번 적재해도 버전이 겹치지 않는다")
    void buildsUniqueVersionOnSameDay() {
        String first = importService.importFrom(sampleFile(), DOWNLOADED_ON).snapshot().getVersion();
        String second = importService.importFrom(sampleFile(), DOWNLOADED_ON).snapshot().getVersion();

        assertThat(second).isNotEqualTo(first);
    }

    /** 강원이 아닌 시·도만 든 파일. 헤더는 정상이라 헤더 검증이 아니라 범위 검증에 걸린다. */
    private static String onlyOtherProvince() {
        String header = String.join(",",
                com.mamoki.tour.domain.parking.importer.ParkingCatalogCsvParser.REQUIRED_HEADERS);

        String[] columns = new String[34];
        java.util.Arrays.fill(columns, "");
        columns[0] = "345-3-000171";
        columns[1] = "신안국민체육관";
        columns[6] = "39";
        columns[31] = "2026-08-04";
        columns[33] = "전남광주통합특별시 신안군";

        return header + "\n" + String.join(",", columns);
    }
}
