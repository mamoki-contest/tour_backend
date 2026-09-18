package com.mamoki.tour.domain.visitorstats.importer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.visitorstats.entity.VisitorStatsEntry;
import com.mamoki.tour.domain.visitorstats.entity.VisitorStatsSnapshot;
import com.mamoki.tour.domain.visitorstats.repository.VisitorStatsEntryRepository;
import com.mamoki.tour.domain.visitorstats.repository.VisitorStatsSnapshotRepository;
import com.mamoki.tour.domain.visitorstats.service.VisitorStatsSnapshotService;
import com.mamoki.tour.global.enums.CatalogMatchStatus;
import com.mamoki.tour.global.enums.SnapshotStatus;
import com.mamoki.tour.global.enums.VisitorCountStatus;

/**
 * 읽어낸 내용을 스냅샷으로 기록한다.
 *
 * <p>성공 기록과 실패 기록은 트랜잭션을 나눈다. 실패를 같은 트랜잭션에 적으면 롤백될 때
 * 이력까지 사라져 무엇이 왜 실패했는지 남지 않는다.
 */
@Service
public class VisitorStatsSnapshotWriter {

    private final VisitorStatsSnapshotRepository snapshotRepository;
    private final VisitorStatsEntryRepository entryRepository;
    private final AttractionRepository attractionRepository;
    private final VisitorStatsSnapshotService snapshotService;

    public VisitorStatsSnapshotWriter(VisitorStatsSnapshotRepository snapshotRepository,
                                      VisitorStatsEntryRepository entryRepository,
                                      AttractionRepository attractionRepository,
                                      VisitorStatsSnapshotService snapshotService) {
        this.snapshotRepository = snapshotRepository;
        this.entryRepository = entryRepository;
        this.attractionRepository = attractionRepository;
        this.snapshotService = snapshotService;
    }

    @Transactional
    public VisitorStatsImportResult persist(VisitorStatsWorkbook workbook, String sourceName,
                                            LocalDate downloadedOn, LocalDate today) {

        VisitorStatsSnapshot snapshot = snapshotRepository.save(VisitorStatsSnapshot.builder()
                .version(nextVersion(workbook.publishedMonth(), downloadedOn))
                .publishedMonth(workbook.publishedMonth())
                .sourcePeriod(workbook.sourcePeriod())
                .countStatus(countStatus(workbook.publishedMonth(), today))
                .downloadedOn(downloadedOn)
                .importedAt(LocalDateTime.now())
                .status(SnapshotStatus.IMPORTING)
                .sourceFileName(sourceName)
                .rowCount(0)
                .build());

        Map<String, String> catalog = loadCatalog();

        int matched = 0;

        for (VisitorStatsRow row : workbook.rows()) {
            String normalized = PlaceNameNormalizer.normalize(row.placeName());
            String contentId = normalized == null ? null : catalog.get(key(row.regionName(), normalized));

            entryRepository.save(VisitorStatsEntry.builder()
                    .snapshot(snapshot)
                    .rawRegionName(row.regionName())
                    .rawPlaceName(row.placeName())
                    .normalizedName(normalized == null ? row.placeName() : normalized)
                    .visitorCount(row.visitorCount())
                    .contentId(contentId)
                    .matchStatus(contentId == null
                            ? CatalogMatchStatus.UNMATCHED : CatalogMatchStatus.MATCHED)
                    .build());

            if (contentId != null) {
                matched++;
            }
        }

        snapshotService.activate(snapshot);

        int total = workbook.rows().size();
        return new VisitorStatsImportResult(snapshot, total, matched, total - matched);
    }

    /** 실패 이력은 본 트랜잭션과 분리해 남긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VisitorStatsSnapshot recordFailure(String sourceName, LocalDate downloadedOn, String reason) {
        VisitorStatsSnapshot failed = snapshotRepository.save(VisitorStatsSnapshot.builder()
                .version(nextVersion("unknown", downloadedOn))
                .publishedMonth("000000")
                .sourcePeriod("unknown")
                .countStatus(VisitorCountStatus.PROVISIONAL)
                .downloadedOn(downloadedOn)
                .importedAt(LocalDateTime.now())
                .status(SnapshotStatus.IMPORTING)
                .sourceFileName(sourceName)
                .rowCount(0)
                .build());

        failed.fail(reason);
        return failed;
    }

    /**
     * 시·군명과 관광지명을 함께 써서 잇는다.
     *
     * <p>이름만으로 이으면 같은 이름이 여러 시·군에 있을 때 엉뚱한 곳에 값이 붙는다.
     * 공식 파일은 (시·군, 관광지명) 이 유일하므로 이 조합이면 한 곳으로 좁혀진다.
     *
     * <p>카탈로그가 비어 있으면 매칭은 0건이다. 값은 그대로 남고 조회에만 노출되지 않는다.
     */
    private Map<String, String> loadCatalog() {
        Map<String, String> byRegionAndName = new HashMap<>();

        for (Attraction attraction : attractionRepository.findAllWithRegion()) {
            String normalized = PlaceNameNormalizer.normalize(attraction.getName());
            String region = attraction.getRegionCode() == null
                    ? null : attraction.getRegionCode().getName();

            if (normalized != null && region != null) {
                byRegionAndName.putIfAbsent(key(region, normalized), attraction.getContentId());
            }
        }

        return byRegionAndName;
    }

    private static String key(String regionName, String normalizedName) {
        return regionName.strip() + "|" + normalizedName;
    }

    /**
     * 확정치는 이듬해 4월에 공표된다. 그 시점을 지난 기간만 확정으로 본다.
     *
     * <p>파일에는 잠정/확정 표시가 없다. 공표 규칙으로 판단할 뿐 파일 내용으로 추정하지 않는다.
     */
    private static VisitorCountStatus countStatus(String publishedMonth, LocalDate today) {
        int publishedYear = Integer.parseInt(publishedMonth.substring(0, 4));
        LocalDate confirmedFrom = LocalDate.of(publishedYear + 1, 4, 1);

        return today.isBefore(confirmedFrom)
                ? VisitorCountStatus.PROVISIONAL
                : VisitorCountStatus.CONFIRMED;
    }

    private String nextVersion(String publishedMonth, LocalDate downloadedOn) {
        String base = publishedMonth + "_" + downloadedOn;

        if (!snapshotRepository.existsByVersion(base)) {
            return base;
        }

        for (int suffix = 2; suffix < 100; suffix++) {
            String candidate = base + "-" + suffix;

            if (!snapshotRepository.existsByVersion(candidate)) {
                return candidate;
            }
        }

        throw new VisitorStatsImportException("같은 날 같은 공표월을 너무 많이 적재했습니다: " + base);
    }
}
