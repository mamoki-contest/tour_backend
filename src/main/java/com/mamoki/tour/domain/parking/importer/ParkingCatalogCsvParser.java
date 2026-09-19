package com.mamoki.tour.domain.parking.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * 전국주차장정보표준데이터 CSV 판독기.
 *
 * <p>공급자 파일은 <b>CP949</b> 다. UTF-8 로 읽으면 한글이 통째로 깨지고, 깨진 제공기관명은
 * 강원 판정에 걸리지 않아 결과가 0행이 된다. 인코딩 실수가 "강원에 주차장이 없다"로 보이는
 * 구조라 판독기가 인코딩을 고정한다.
 *
 * <p>요금정보·특기사항·주소 열의 값에 콤마가 들어가므로 직접 쪼개지 않고 RFC 4180 따옴표
 * 규칙을 따르는 파서를 쓴다.
 *
 * <p>전국 18,883행이 한 파일로 오지만 <b>제공기관명이 {@code 강원특별자치도 } 로 시작하는
 * 행만</b> 담는다. 나머지 시·도는 이 서비스의 범위 밖이라, 그쪽 행이 깨져 있어도 강원 적재를
 * 막지 않는다. 반대로 강원 행이 깨져 있으면 파일을 통째로 거부한다. 일부만 읽어 두면 빠진
 * 주차장이 원래 없는 것인지 파일이 깨진 것인지 구분할 수 없다.
 */
public final class ParkingCatalogCsvParser {

    /** 공급자가 정한 표준 헤더 34열. 한 열이라도 다르면 파일을 거부한다. */
    public static final List<String> REQUIRED_HEADERS = List.of(
            "주차장관리번호", "주차장명", "주차장구분", "주차장유형", "소재지도로명주소", "소재지지번주소",
            "주차구획수", "급지구분", "부제시행구분", "운영요일",
            "평일운영시작시각", "평일운영종료시각", "토요일운영시작시각", "토요일운영종료시각",
            "공휴일운영시작시각", "공휴일운영종료시각", "요금정보",
            "주차기본시간", "주차기본요금", "추가단위시간", "추가단위요금",
            "1일주차권요금적용시간", "1일주차권요금", "월정기권요금", "결제방법", "특기사항",
            "관리기관명", "전화번호", "위도", "경도", "장애인전용주차구역보유여부",
            "데이터기준일자", "제공기관코드", "제공기관명");

    /** 이 서비스가 다루는 범위. 제공기관명이 이 말로 시작하는 행만 적재한다. */
    public static final String GANGWON_PREFIX = "강원특별자치도 ";

    /** 공급자 파일 인코딩. 표준데이터는 UTF-8 이 아니라 CP949 로 배포된다. */
    private static final Charset CP949 = Charset.forName("MS949");

    /** 한반도 남쪽 범위. 이 밖의 값은 좌표가 아니라 오염이다. */
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("33");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("39");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("124");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("132");

    private ParkingCatalogCsvParser() {
    }

    public static List<ParkingCatalogRow> parse(InputStream in, String fileName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, CP949))) {
            String headerLine = reader.readLine();

            if (headerLine == null) {
                throw new ParkingCatalogImportException("빈 파일입니다: " + fileName);
            }

            validateHeader(headerLine, fileName);

            return readGangwonRows(reader, fileName);

        } catch (IOException e) {
            throw new ParkingCatalogImportException("파일을 읽지 못했습니다: " + fileName, e);
        }
    }

    private static void validateHeader(String headerLine, String fileName) {
        List<String> headers = new ArrayList<>();

        for (String header : headerLine.split(",", -1)) {
            headers.add(header.trim());
        }

        if (!REQUIRED_HEADERS.equals(headers)) {
            throw new ParkingCatalogImportException(
                    "헤더가 예상과 다릅니다: %s (기대 %d열, 실제 %d열)"
                            .formatted(fileName, REQUIRED_HEADERS.size(), headers.size()));
        }
    }

    private static List<ParkingCatalogRow> readGangwonRows(BufferedReader reader, String fileName)
            throws IOException {

        List<ParkingCatalogRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader(REQUIRED_HEADERS.toArray(String[]::new))
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get()
                .parse(reader)) {

            for (CSVRecord record : parser) {
                if (!isGangwon(record)) {
                    continue;
                }

                ParkingCatalogRow row = toRow(record, fileName);

                if (!seen.add(row.managementNumber())) {
                    throw new ParkingCatalogImportException(
                            "주차장관리번호가 중복입니다: %s %d행 (%s)"
                                    .formatted(fileName, line(record), row.managementNumber()));
                }

                rows.add(row);
            }
        }

        if (rows.isEmpty()) {
            throw new ParkingCatalogImportException(
                    "강원 행이 없습니다: %s (제공기관명이 '%s' 로 시작하는 행을 찾지 못했습니다)"
                            .formatted(fileName, GANGWON_PREFIX));
        }

        return rows;
    }

    /** 제공기관명으로 가른다. 주소로 가르면 경계 지역 표기 차이에 흔들린다. */
    private static boolean isGangwon(CSVRecord record) {
        String provider = optionalText(record, "제공기관명");

        return provider != null && provider.startsWith(GANGWON_PREFIX);
    }

    private static ParkingCatalogRow toRow(CSVRecord record, String fileName) {
        long line = line(record);

        return new ParkingCatalogRow(
                requiredText(record, "주차장관리번호", fileName, line),
                requiredText(record, "주차장명", fileName, line),
                optionalText(record, "주차장구분"),
                optionalText(record, "주차장유형"),
                optionalText(record, "소재지도로명주소"),
                optionalText(record, "소재지지번주소"),
                capacity(record, fileName, line),
                optionalText(record, "운영요일"),
                optionalText(record, "평일운영시작시각"),
                optionalText(record, "평일운영종료시각"),
                optionalText(record, "토요일운영시작시각"),
                optionalText(record, "토요일운영종료시각"),
                optionalText(record, "공휴일운영시작시각"),
                optionalText(record, "공휴일운영종료시각"),
                optionalText(record, "요금정보"),
                coordinate(record, "위도", MIN_LATITUDE, MAX_LATITUDE, fileName, line),
                coordinate(record, "경도", MIN_LONGITUDE, MAX_LONGITUDE, fileName, line),
                dataBaseDate(record, fileName, line),
                optionalText(record, "제공기관코드"),
                requiredText(record, "제공기관명", fileName, line));
    }

    private static int capacity(CSVRecord record, String fileName, long line) {
        String value = requiredText(record, "주차구획수", fileName, line);

        try {
            int parsed = Integer.parseInt(value);

            if (parsed < 0) {
                throw new NumberFormatException(value);
            }

            return parsed;
        } catch (NumberFormatException e) {
            throw new ParkingCatalogImportException(
                    "주차구획수 값이 올바르지 않습니다: %s %d행 (%s)".formatted(fileName, line, value));
        }
    }

    /**
     * 좌표는 비어 있을 수 있다. 빈 값은 null 로 두되, <b>값이 있는데 숫자가 아니거나 한반도
     * 밖이면 파일을 거부한다.</b> 조용히 버리면 그 주차장만 반경 조회에서 사라져 "주차장 없음"
     * 으로 보인다.
     */
    private static BigDecimal coordinate(CSVRecord record, String column,
                                         BigDecimal min, BigDecimal max,
                                         String fileName, long line) {
        String value = optionalText(record, column);

        if (value == null) {
            return null;
        }

        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new ParkingCatalogImportException(
                    "%s 값이 숫자가 아닙니다: %s %d행 (%s)".formatted(column, fileName, line, value));
        }

        if (parsed.compareTo(min) < 0 || parsed.compareTo(max) > 0) {
            throw new ParkingCatalogImportException(
                    "%s 값이 한반도 범위를 벗어났습니다: %s %d행 (%s)"
                            .formatted(column, fileName, line, value));
        }

        return parsed;
    }

    private static LocalDate dataBaseDate(CSVRecord record, String fileName, long line) {
        String value = requiredText(record, "데이터기준일자", fileName, line);

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ParkingCatalogImportException(
                    "데이터기준일자가 yyyy-MM-dd 형식이 아닙니다: %s %d행 (%s)"
                            .formatted(fileName, line, value));
        }
    }

    private static String requiredText(CSVRecord record, String column,
                                       String fileName, long line) {
        String value = optionalText(record, column);

        if (value == null) {
            throw new ParkingCatalogImportException(
                    "%s 값이 비어 있습니다: %s %d행".formatted(column, fileName, line));
        }

        return value;
    }

    private static String optionalText(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) {
            return null;
        }

        String value = record.get(column).strip();
        return value.isEmpty() ? null : value;
    }

    /** 헤더 줄을 미리 읽어 냈으므로 파일 기준 줄 번호는 하나 뒤다. */
    private static long line(CSVRecord record) {
        return record.getRecordNumber() + 1;
    }
}
