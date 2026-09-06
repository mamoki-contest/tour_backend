package com.mamoki.tour.domain.tmaprank.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 데이터랩 다운로드 zip 하나를 읽어 검증한다.
 *
 * <p>zip 에는 세대별 CSV 6종이 들어 있다. 하나라도 빠져 있으면 다운로드가 중간에 끊긴
 * 것으로 보고 거부한다. 이슈의 "부분 파일은 적재하지 않는다" 를 여기서 지킨다.
 *
 * <p>연령대는 파일명이 아니라 CSV 안의 값으로 판단한다. 파일명은 표기가 바뀔 수 있지만
 * 데이터 자체는 그렇지 않다.
 */
public final class TmapRankZipReader {

    static final Set<String> REQUIRED_AGE_GROUPS = Set.of("전체", "20", "30", "40", "50", "60");

    private TmapRankZipReader() {
    }

    public static TmapRankZipContent read(Path zipPath) {
        TmapRankFileName source = TmapRankFileName.parse(zipPath.getFileName().toString());

        Map<String, List<TmapRankCsvRow>> byAgeGroup = readAgeGroups(zipPath);

        if (!byAgeGroup.keySet().equals(REQUIRED_AGE_GROUPS)) {
            throw new TmapRankImportException(
                    "세대별 파일이 온전하지 않습니다: %s (기대: %s, 실제: %s)"
                            .formatted(zipPath.getFileName(),
                                    sorted(REQUIRED_AGE_GROUPS), sorted(byAgeGroup.keySet())));
        }

        return new TmapRankZipContent(
                source,
                byAgeGroup.get(TmapRankCsvRow.AGE_GROUP_ALL),
                sorted(byAgeGroup.keySet()));
    }

    private static Map<String, List<TmapRankCsvRow>> readAgeGroups(Path zipPath) {
        Map<String, List<TmapRankCsvRow>> byAgeGroup = new LinkedHashMap<>();

        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            var entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".csv")) {
                    continue;
                }

                try (InputStream in = zip.getInputStream(entry)) {
                    List<TmapRankCsvRow> rows = TmapRankCsvParser.parse(in, entry.getName());
                    putRows(byAgeGroup, rows, entry.getName(), zipPath);
                }
            }
        } catch (IOException e) {
            throw new TmapRankImportException("zip 을 읽지 못했습니다: " + zipPath.getFileName(), e);
        }

        if (byAgeGroup.isEmpty()) {
            throw new TmapRankImportException("zip 안에 CSV 가 없습니다: " + zipPath.getFileName());
        }

        return byAgeGroup;
    }

    /** 한 파일 안에서 연령대가 섞여 있으면 파일이 잘못 만들어진 것으로 본다. */
    private static void putRows(Map<String, List<TmapRankCsvRow>> byAgeGroup,
                                List<TmapRankCsvRow> rows, String entryName, Path zipPath) {

        String ageGroup = rows.get(0).ageGroup();

        boolean mixed = rows.stream().anyMatch(row -> !ageGroup.equals(row.ageGroup()));
        if (mixed) {
            throw new TmapRankImportException(
                    "한 파일에 여러 연령대가 섞여 있습니다: %s / %s".formatted(zipPath.getFileName(), entryName));
        }

        if (byAgeGroup.putIfAbsent(ageGroup, rows) != null) {
            throw new TmapRankImportException(
                    "같은 연령대 파일이 두 번 들어 있습니다: %s / %s (%s)"
                            .formatted(zipPath.getFileName(), entryName, ageGroup));
        }
    }

    private static List<String> sorted(Set<String> values) {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(String::compareTo);
        return sorted;
    }
}
