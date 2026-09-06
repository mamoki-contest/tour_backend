package com.mamoki.tour.domain.interest.importer;

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
import com.mamoki.tour.domain.interest.entity.AttractionInterest;
import com.mamoki.tour.domain.interest.entity.InterestSnapshot;
import com.mamoki.tour.domain.interest.repository.AttractionInterestRepository;
import com.mamoki.tour.domain.interest.repository.InterestSnapshotRepository;
import com.mamoki.tour.domain.interest.service.InterestSnapshotService;
import com.mamoki.tour.global.enums.InterestMatchStatus;
import com.mamoki.tour.global.enums.SnapshotStatus;

/**
 * 검증을 통과한 내용을 스냅샷으로 기록한다.
 *
 * <p>적재 성공과 실패 기록이 서로 다른 트랜잭션이어야 한다. 실패를 같은 트랜잭션에 적으면
 * 롤백될 때 이력까지 사라져, 무엇이 왜 실패했는지 남지 않는다.
 */
@Service
public class InterestSnapshotWriter {

    private final InterestSnapshotRepository snapshotRepository;
    private final AttractionInterestRepository interestRepository;
    private final AttractionRepository attractionRepository;
    private final InterestSnapshotService snapshotService;

    public InterestSnapshotWriter(InterestSnapshotRepository snapshotRepository,
                                  AttractionInterestRepository interestRepository,
                                  AttractionRepository attractionRepository,
                                  InterestSnapshotService snapshotService) {
        this.snapshotRepository = snapshotRepository;
        this.interestRepository = interestRepository;
        this.attractionRepository = attractionRepository;
        this.snapshotService = snapshotService;
    }

    @Transactional
    public InterestImportResult persist(List<InterestZipContent> contents,
                                        String sourceName, LocalDate downloadedOn) {

        String sourcePeriod = contents.get(0).source().sourcePeriod();
        InterestSnapshot snapshot = snapshotRepository.save(InterestSnapshot.builder()
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

        for (InterestZipContent content : contents) {
            for (InterestCsvRow row : content.allAgesRows()) {
                String normalized = PlaceNameNormalizer.normalize(row.placeName());
                String contentId = normalized == null ? null : catalogByNormalizedName.get(normalized);

                interestRepository.save(AttractionInterest.builder()
                        .snapshot(snapshot)
                        .rawRegionName(content.source().sigungu())
                        .rawPlaceName(row.placeName())
                        .normalizedName(normalized == null ? row.placeName() : normalized)
                        .interestValue(row.ratio())
                        .sourceRank(row.rank())
                        .contentId(contentId)
                        .matchStatus(contentId == null
                                ? InterestMatchStatus.UNMATCHED : InterestMatchStatus.MATCHED)
                        .build());

                total++;
                if (contentId != null) {
                    matched++;
                }
            }
        }

        snapshotService.activate(snapshot);

        return new InterestImportResult(snapshot, contents.size(), total, matched, total - matched);
    }

    /** 실패 이력은 본 트랜잭션과 분리해 남긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InterestSnapshot recordFailure(String sourceName, LocalDate downloadedOn, String reason) {
        InterestSnapshot failed = snapshotRepository.save(InterestSnapshot.builder()
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

    /** 관심도 CSV 에는 좌표가 없어 이름으로만 잇는다. 카탈로그가 비어 있으면 매칭은 0건이다. */
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

        throw new InterestImportException("같은 날 적재 횟수가 너무 많습니다: " + base);
    }
}
