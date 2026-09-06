package com.mamoki.tour.domain.interest.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * 데이터랩 관심도 CSV 판독기.
 *
 * <p>공급자 파일에는 두 가지 특성이 있다. 파일 앞에 UTF-8 BOM 이 붙어 있어 걷어내지 않으면
 * 첫 컬럼명이 달라지고, 각 데이터 행 끝에 탭 문자가 붙어 있어 마지막 값이 그대로는 숫자로
 * 해석되지 않는다. 둘 다 여기서 흡수한다.
 *
 * <p>헤더가 다르거나 값이 형식에 맞지 않으면 그 파일을 통째로 거부한다. 일부만 읽어 두면
 * 빠진 장소가 관심도 없음인지 파일이 깨진 것인지 구분할 수 없다.
 */
public final class InterestCsvParser {

    static final List<String> REQUIRED_HEADERS =
            List.of("순위", "관광지ID", "관심지점명", "구분", "연령대", "비율");

    private static final char BOM = '\uFEFF';

    private InterestCsvParser() {
    }

    public static List<InterestCsvRow> parse(InputStream in, String fileName) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();

            if (headerLine == null) {
                throw new InterestImportException("빈 파일입니다: " + fileName);
            }

            validateHeader(stripBom(headerLine), fileName);

            return readRows(reader, fileName);

        } catch (IOException e) {
            throw new InterestImportException("파일을 읽지 못했습니다: " + fileName, e);
        }
    }

    private static String stripBom(String line) {
        return !line.isEmpty() && line.charAt(0) == BOM ? line.substring(1) : line;
    }

    private static void validateHeader(String headerLine, String fileName) {
        List<String> headers = new ArrayList<>();
        for (String header : headerLine.split(",", -1)) {
            headers.add(header.trim());
        }

        if (!REQUIRED_HEADERS.equals(headers)) {
            throw new InterestImportException(
                    "헤더가 예상과 다릅니다: %s (기대: %s, 실제: %s)"
                            .formatted(fileName, REQUIRED_HEADERS, headers));
        }
    }

    private static List<InterestCsvRow> readRows(BufferedReader reader, String fileName)
            throws IOException {

        List<InterestCsvRow> rows = new ArrayList<>();

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader(REQUIRED_HEADERS.toArray(String[]::new))
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get()
                .parse(reader)) {

            for (CSVRecord record : parser) {
                rows.add(toRow(record, fileName));
            }
        }

        if (rows.isEmpty()) {
            throw new InterestImportException("데이터 행이 없습니다: " + fileName);
        }

        return rows;
    }

    private static InterestCsvRow toRow(CSVRecord record, String fileName) {
        long line = record.getRecordNumber() + 1;

        return new InterestCsvRow(
                requiredPositiveInt(record, "순위", fileName, line),
                requiredText(record, "관광지ID", fileName, line),
                requiredText(record, "관심지점명", fileName, line),
                optionalText(record, "구분"),
                requiredText(record, "연령대", fileName, line),
                requiredRatio(record, fileName, line));
    }

    private static String requiredText(CSVRecord record, String column, String fileName, long line) {
        String value = optionalText(record, column);

        if (value == null) {
            throw new InterestImportException(
                    "필수 값이 비어 있습니다: %s %d행 %s".formatted(fileName, line, column));
        }

        return value;
    }

    private static String optionalText(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) {
            return null;
        }

        // 공급자가 행 끝에 붙이는 탭 문자를 함께 걷어낸다.
        String value = record.get(column).strip();
        return value.isEmpty() ? null : value;
    }

    private static int requiredPositiveInt(CSVRecord record, String column,
                                           String fileName, long line) {
        String value = requiredText(record, column, fileName, line);

        try {
            int parsed = Integer.parseInt(value);

            if (parsed <= 0) {
                throw new NumberFormatException(value);
            }

            return parsed;
        } catch (NumberFormatException e) {
            throw new InterestImportException(
                    "%s 값이 올바르지 않습니다: %s %d행 (%s)".formatted(column, fileName, line, value));
        }
    }

    private static BigDecimal requiredRatio(CSVRecord record, String fileName, long line) {
        String value = requiredText(record, "비율", fileName, line);

        try {
            BigDecimal parsed = new BigDecimal(value);

            if (parsed.signum() < 0) {
                throw new NumberFormatException(value);
            }

            return parsed;
        } catch (NumberFormatException e) {
            throw new InterestImportException(
                    "비율 값이 올바르지 않습니다: %s %d행 (%s)".formatted(fileName, line, value));
        }
    }
}
