package com.mamoki.tour.domain.parking.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 운영자가 내려받은 전국주차장정보표준데이터 CSV 를 하나의 스냅샷으로 적재한다.
 *
 * <p>읽기와 검증을 모두 마친 뒤에 기록을 시작한다. 중간에 실패하면 아무것도 적재되지 않고
 * 직전 정상 스냅샷이 그대로 활성 상태로 남는다. 반기 갱신이라 적재는 드물지만, 실패한
 * 적재가 직전 데이터를 지워 버리면 그때부터 모든 관광지가 "주차장 정보 없음" 이 된다.
 */
@Service
public class ParkingCatalogImportService {

    private static final Logger log = LoggerFactory.getLogger(ParkingCatalogImportService.class);

    private static final int MAX_FAILURE_REASON = 500;

    private final ParkingCatalogSnapshotWriter snapshotWriter;

    public ParkingCatalogImportService(ParkingCatalogSnapshotWriter snapshotWriter) {
        this.snapshotWriter = snapshotWriter;
    }

    /**
     * @param file         공공데이터포털에서 받은 표준데이터 CSV (CP949)
     * @param downloadedOn 운영자가 파일을 내려받은 날
     */
    public ParkingCatalogImportResult importFrom(Path file, LocalDate downloadedOn) {
        String sourceName = file.getFileName() == null
                ? file.toString() : file.getFileName().toString();

        // 파일이 없으면 적재를 시작한 적도 없다. 이것까지 FAILED 스냅샷으로 남기면 경로를
        // 잘못 친 실행이 이력에 쌓여, 정작 봐야 할 진짜 적재 실패가 그 사이에 묻힌다.
        // 스냅샷 행을 만드는 것은 읽을 파일이 있다고 확인한 뒤다.
        if (!Files.isRegularFile(file)) {
            throw new ParkingCatalogImportException("파일이 아닙니다: " + file);
        }

        try {
            List<ParkingCatalogRow> rows = read(file, sourceName);

            ParkingCatalogImportResult result =
                    snapshotWriter.persist(rows, sourceName, downloadedOn);

            log.info("주차장 표준데이터 적재 완료: version={}, 기준일={}, 행={}, 좌표있음={}, 시·군={}",
                    result.snapshot().getVersion(), result.snapshot().getDataBaseDate(),
                    result.totalRows(), result.withCoordinates(), result.districtCount());

            return result;

        } catch (ParkingCatalogImportException e) {
            snapshotWriter.recordFailure(sourceName, downloadedOn, truncate(e.getMessage()));
            throw e;
        }
    }

    /** 파일이 있다는 것은 {@link #importFrom} 이 이미 확인했다. */
    private List<ParkingCatalogRow> read(Path file, String sourceName) {
        try (InputStream in = Files.newInputStream(file)) {
            return ParkingCatalogCsvParser.parse(in, sourceName);
        } catch (IOException e) {
            throw new ParkingCatalogImportException("파일을 열지 못했습니다: " + file, e);
        }
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return "알 수 없는 오류";
        }

        return reason.length() <= MAX_FAILURE_REASON
                ? reason
                : reason.substring(0, MAX_FAILURE_REASON);
    }
}
