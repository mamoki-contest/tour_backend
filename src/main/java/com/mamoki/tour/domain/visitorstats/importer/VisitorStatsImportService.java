package com.mamoki.tour.domain.visitorstats.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 운영자가 내려받은 입장객통계 엑셀을 하나의 스냅샷으로 적재한다.
 *
 * <p>관광지식정보시스템에서 강원 전체를 한 파일로 받는다. TMAP 순위처럼 시·군별로 나뉘지
 * 않아 파일 하나가 곧 한 번의 적재다.
 *
 * <p>읽기와 검증을 모두 마친 뒤에 기록을 시작한다. 중간에 실패하면 아무것도 적재되지 않고
 * 직전 정상 스냅샷이 그대로 활성 상태로 남는다.
 */
@Service
public class VisitorStatsImportService {

    private static final Logger log = LoggerFactory.getLogger(VisitorStatsImportService.class);

    private static final int MAX_FAILURE_REASON = 500;

    private final VisitorStatsSnapshotWriter snapshotWriter;

    public VisitorStatsImportService(VisitorStatsSnapshotWriter snapshotWriter) {
        this.snapshotWriter = snapshotWriter;
    }

    public VisitorStatsImportResult importFrom(Path file, LocalDate downloadedOn) {
        return importFrom(file, downloadedOn, LocalDate.now());
    }

    /**
     * @param file         공식 다운로드로 받은 xls 파일
     * @param downloadedOn 운영자가 파일을 내려받은 날
     * @param today        잠정/확정 판단 기준일
     */
    public VisitorStatsImportResult importFrom(Path file, LocalDate downloadedOn, LocalDate today) {
        String sourceName = file.getFileName() == null
                ? file.toString() : file.getFileName().toString();

        try {
            VisitorStatsWorkbook workbook = read(file);

            VisitorStatsImportResult result =
                    snapshotWriter.persist(workbook, sourceName, downloadedOn, today);

            log.info("입장객통계 스냅샷 적재 완료: version={}, 공표월={}, 행={}, 매칭={}, 미매칭={}",
                    result.snapshot().getVersion(), result.snapshot().getPublishedMonth(),
                    result.totalRows(), result.matchedRows(), result.unmatchedRows());

            return result;

        } catch (VisitorStatsImportException e) {
            snapshotWriter.recordFailure(sourceName, downloadedOn, truncate(e.getMessage()));
            throw e;
        }
    }

    private VisitorStatsWorkbook read(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new VisitorStatsImportException("파일이 아닙니다: " + file);
        }

        try (InputStream in = Files.newInputStream(file)) {
            return VisitorStatsExcelParser.parse(in);
        } catch (IOException e) {
            throw new VisitorStatsImportException("파일을 열지 못했습니다: " + file, e);
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
