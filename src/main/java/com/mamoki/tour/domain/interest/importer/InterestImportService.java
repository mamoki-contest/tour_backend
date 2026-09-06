package com.mamoki.tour.domain.interest.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;

/**
 * 운영자가 내려받은 관심도 파일 묶음을 하나의 스냅샷으로 적재한다.
 *
 * <p>강원 18개 시·군을 각각 내려받으므로 한 번의 적재는 zip 18개다. 하나라도 빠지면
 * 그 시·군의 관심도가 통째로 사라지는데, 그것이 `관심도 없음`과 구분되지 않는다.
 * 그래서 지역이 모두 갖춰졌을 때만 적재한다.
 *
 * <p>읽기와 검증을 모두 마친 뒤에 기록을 시작한다. 중간에 실패하면 아무것도 적재되지 않고
 * 직전 정상 스냅샷이 그대로 활성 상태로 남는다.
 */
@Service
public class InterestImportService {

    /** 한국관광공사 영역 코드. 강원. */
    private static final String GANGWON_AREA_CODE = "32";

    private static final Logger log = LoggerFactory.getLogger(InterestImportService.class);

    private final RegionCodeRepository regionCodeRepository;
    private final InterestSnapshotWriter snapshotWriter;

    public InterestImportService(RegionCodeRepository regionCodeRepository,
                                 InterestSnapshotWriter snapshotWriter) {
        this.regionCodeRepository = regionCodeRepository;
        this.snapshotWriter = snapshotWriter;
    }

    /**
     * @param directory    zip 18개가 들어 있는 디렉터리
     * @param downloadedOn 운영자가 파일을 내려받은 날
     */
    public InterestImportResult importFrom(Path directory, LocalDate downloadedOn) {
        String sourceName = directory.getFileName() == null
                ? directory.toString() : directory.getFileName().toString();

        try {
            List<InterestZipContent> contents = readAll(directory);
            validateRegionsAreComplete(contents);
            validateSinglePeriod(contents);

            InterestImportResult result = snapshotWriter.persist(contents, sourceName, downloadedOn);

            log.info("관심도 스냅샷 적재 완료: version={}, 지역={}, 행={}, 매칭={}",
                    result.snapshot().getVersion(), result.regionCount(),
                    result.totalRows(), result.matchedRows());

            return result;

        } catch (InterestImportException e) {
            snapshotWriter.recordFailure(sourceName, downloadedOn, truncate(e.getMessage()));
            throw e;
        }
    }

    private List<InterestZipContent> readAll(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new InterestImportException("디렉터리가 아닙니다: " + directory);
        }

        List<Path> zips;
        try (Stream<Path> files = Files.list(directory)) {
            zips = files.filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new InterestImportException("디렉터리를 읽지 못했습니다: " + directory, e);
        }

        if (zips.isEmpty()) {
            throw new InterestImportException("적재할 zip 이 없습니다: " + directory);
        }

        List<InterestZipContent> contents = new ArrayList<>();
        for (Path zip : zips) {
            contents.add(InterestZipReader.read(zip));
        }

        return contents;
    }

    /** 강원 18개 시·군이 모두 있어야 한다. 빠진 지역이 있으면 부분 적재가 된다. */
    private void validateRegionsAreComplete(List<InterestZipContent> contents) {
        Set<String> expected = new HashSet<>();
        for (RegionCode region : regionCodeRepository.findAllByAreaCode(GANGWON_AREA_CODE)) {
            expected.add(region.getName());
        }

        if (expected.isEmpty()) {
            throw new InterestImportException("지역코드 매핑이 비어 있어 지역을 확인할 수 없습니다.");
        }

        Set<String> actual = new HashSet<>();
        for (InterestZipContent content : contents) {
            if (!actual.add(content.source().sigungu())) {
                throw new InterestImportException(
                        "같은 시·군 파일이 두 번 들어 있습니다: " + content.source().sigungu());
            }
        }

        Set<String> unknown = new HashSet<>(actual);
        unknown.removeAll(expected);
        if (!unknown.isEmpty()) {
            throw new InterestImportException("알 수 없는 시·군이 있습니다: " + sorted(unknown));
        }

        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        if (!missing.isEmpty()) {
            throw new InterestImportException(
                    "시·군 파일이 빠졌습니다 (%d/%d): %s"
                            .formatted(actual.size(), expected.size(), sorted(missing)));
        }
    }

    /** 한 스냅샷 안에서 원천 조회기간이 섞이면 값의 기준이 달라진다. */
    private void validateSinglePeriod(List<InterestZipContent> contents) {
        Set<String> periods = new HashSet<>();
        for (InterestZipContent content : contents) {
            periods.add(content.source().sourcePeriod());
        }

        if (periods.size() > 1) {
            throw new InterestImportException("원천 조회기간이 섞여 있습니다: " + sorted(periods));
        }
    }

    private static List<String> sorted(Set<String> values) {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(String::compareTo);
        return sorted;
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return "알 수 없는 오류";
        }

        return reason.length() <= 500 ? reason : reason.substring(0, 497) + "...";
    }
}
