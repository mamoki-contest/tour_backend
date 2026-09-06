package com.mamoki.tour.domain.tmaprank.importer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankEntry;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankSnapshot;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankEntryRepository;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankSnapshotRepository;
import com.mamoki.tour.domain.tmaprank.service.TmapRankSnapshotService;
import com.mamoki.tour.global.enums.CatalogMatchStatus;
import com.mamoki.tour.global.enums.SnapshotStatus;

/**
 * 검증을 통과한 내용을 스냅샷으로 기록한다.
 *
 * <p>적재 성공과 실패 기록이 서로 다른 트랜잭션이어야 한다. 실패를 같은 트랜잭션에 적으면
 * 롤백될 때 이력까지 사라져, 무엇이 왜 실패했는지 남지 않는다.
 */
@Service
public class TmapRankSnapshotWriter {

    private final TmapRankSnapshotRepository snapshotRepository;
    private final TmapRankEntryRepository tmapRankEntryRepository;
    private final AttractionRepository attractionRepository;
    private final TmapRankSnapshotService snapshotService;

    public TmapRankSnapshotWriter(TmapRankSnapshotRepository snapshotRepository,
                                  TmapRankEntryRepository tmapRankEntryRepository,
                                  AttractionRepository attractionRepository,
                                  TmapRankSnapshotService snapshotService) {
        this.snapshotRepository = snapshotRepository;
        this.tmapRankEntryRepository = tmapRankEntryRepository;
        this.attractionRepository = attractionRepository;
        this.snapshotService = snapshotService;
    }

    @Transactional
    public TmapRankImportResult persist(List<TmapRankZipContent> contents,
                                        String sourceName, LocalDate downloadedOn) {

        String sourcePeriod = contents.get(0).source().sourcePeriod();
        TmapRankSnapshot snapshot = snapshotRepository.save(TmapRankSnapshot.builder()
                .version(nextVersion(sourcePeriod, downloadedOn))
                .sourcePeriod(sourcePeriod)
                .downloadedOn(downloadedOn)
                .importedAt(LocalDateTime.now())
                .status(SnapshotStatus.IMPORTING)
                .sourceFileName(sourceName)
                .rowCount(0)
                .build());

        Map<String, String> catalogByNormalizedName = loadCatalog();

        int matched = 0;
        int total = 0;

        for (TmapRankZipContent content : contents) {
            for (TmapRankCsvRow row : content.allAgesRows()) {
                String normalized = PlaceNameNormalizer.normalize(row.placeName());
                String contentId = normalized == null ? null : catalogByNormalizedName.get(normalized);

                tmapRankEntryRepository.save(TmapRankEntry.builder()
                        .snapshot(snapshot)
                        .rawRegionName(content.source().sigungu())
                        .rawPlaceName(row.placeName())
                        .normalizedName(normalized == null ? row.placeName() : normalized)
                        .searchRatio(row.ratio())
                        .sourceRank(row.rank())
                        .contentId(contentId)
                        .matchStatus(contentId == null
                                ? CatalogMatchStatus.UNMATCHED : CatalogMatchStatus.MATCHED)
                        .build());

                total++;
                if (contentId != null) {
                    matched++;
                }
            }
        }

        snapshotService.activate(snapshot);

        return new TmapRankImportResult(snapshot, contents.size(), total, matched, total - matched);
    }

    /** 실패 이력은 본 트랜잭션과 분리해 남긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TmapRankSnapshot recordFailure(String sourceName, LocalDate downloadedOn, String reason) {
        TmapRankSnapshot failed = snapshotRepository.save(TmapRankSnapshot.builder()
                .version(nextVersion("unknown", downloadedOn))
                .sourcePeriod("unknown")
                .downloadedOn(downloadedOn)
                .importedAt(LocalDateTime.now())
                .status(SnapshotStatus.IMPORTING)
                .sourceFileName(sourceName)
                .rowCount(0)
                .build());

        failed.fail(reason);
        return failed;
    }

    /** TMAP CSV 에는 좌표가 없어 이름으로만 잇는다. 카탈로그가 비어 있으면 매칭은 0건이다. */
    private Map<String, String> loadCatalog() {
        Map<String, String> byNormalizedName = new HashMap<>();

        for (Attraction attraction : attractionRepository.findAll()) {
            String normalized = PlaceNameNormalizer.normalize(attraction.getName());

            if (normalized != null) {
                byNormalizedName.putIfAbsent(normalized, attraction.getContentId());
            }
        }

        return byNormalizedName;
    }

    private String nextVersion(String sourcePeriod, LocalDate downloadedOn) {
        String base = sourcePeriod + "_" + downloadedOn;

        if (!snapshotRepository.existsByVersion(base)) {
            return base;
        }

        for (int sequence = 2; sequence < 1000; sequence++) {
            String candidate = base + "-" + sequence;

            if (!snapshotRepository.existsByVersion(candidate)) {
                return candidate;
            }
        }

        throw new TmapRankImportException("같은 날 적재 횟수가 너무 많습니다: " + base);
    }
}
