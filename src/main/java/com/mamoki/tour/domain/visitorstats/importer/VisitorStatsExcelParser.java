package com.mamoki.tour.domain.visitorstats.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * 관광지식정보시스템 주요관광지점 입장객통계 엑셀을 읽는다.
 *
 * <p>머리글이 두 줄이다. 첫 줄에 고정 항목(시도·군구·관광지·내/외국인)과 연도가, 둘째 줄에
 * `2026년 03월` 같은 월이 들어온다. 월 열의 위치는 파일이 담은 기간에 따라 달라지므로
 * 자리를 고정하지 않고 머리글에서 찾는다.
 *
 * <p>공표월은 <b>파일에 나오는 모든 시·군에 값이 있는 마지막 월</b>이다. 두 가지를 동시에
 * 피하려는 기준이다.
 *
 * <ul>
 *   <li>아직 공표되지 않은 달의 열이 미리 만들어져 비어 있다. 2026-09-18 확인 시점에
 *       2026-04~06 열이 있었으나 279곳 전부 비어 있었다. 머리글의 마지막 열을 믿으면
 *       전 관광지가 0명으로 내려간다.</li>
 *   <li>분기 잠정 집계 중에는 일부 지자체가 아직 제출하지 않는다. 같은 시점에 2026-01~03 은
 *       18개 시·군 중 12개만 있었다. 값이 있는 마지막 월을 그대로 쓰면 강릉시·속초시·원주시
 *       관광지가 통째로 통계 없음으로 보인다. 그 시·군이 미등록인 것과 구분되지 않는다.</li>
 * </ul>
 *
 * <p>그래서 커버리지가 온전한 마지막 달을 고른다. 최신은 아니지만 모든 시·군이 같은 기준 월을
 * 쓰게 되고, 빠진 시·군이 생기지 않는다.
 *
 * <p>관광지마다 `내국인`·`외국인`·`합계` 행이 온다. <b>`합계` 행만</b> 쓴다. 외국인 행이
 * 아예 없는 관광지도 있어 우리가 더하면 합이 달라질 수 있고, 공식 파일이 이미 더해 놓았다.
 */
public final class VisitorStatsExcelParser {

    private static final int REGION_SIDO_COLUMN = 0;
    private static final int REGION_SIGUNGU_COLUMN = 1;
    private static final int PLACE_COLUMN = 2;
    private static final int VISITOR_KIND_COLUMN = 3;

    private static final int HEADER_ROW = 0;
    private static final int MONTH_HEADER_ROW = 1;
    private static final int FIRST_DATA_ROW = 2;

    /** 내국인과 외국인을 합쳐 놓은 행. 이 행만 적재한다. */
    private static final String TOTAL_KIND = "합계";

    /** `2026년 03월` 형태. 공백 유무가 파일마다 흔들려 느슨하게 맞춘다. */
    private static final Pattern MONTH_HEADER = Pattern.compile("(\\d{4})\\s*년\\s*(\\d{1,2})\\s*월");

    private VisitorStatsExcelParser() {
    }

    public static VisitorStatsWorkbook parse(InputStream in) {
        try (HSSFWorkbook workbook = new HSSFWorkbook(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new VisitorStatsImportException("시트가 없습니다.");
            }

            return parseSheet(workbook.getSheetAt(0));

        } catch (IOException e) {
            throw new VisitorStatsImportException("엑셀 파일을 읽지 못했습니다.", e);
        }
    }

    private static VisitorStatsWorkbook parseSheet(Sheet sheet) {
        validateFixedHeader(sheet);

        Map<String, Integer> monthColumns = readMonthColumns(sheet);

        if (monthColumns.isEmpty()) {
            throw new VisitorStatsImportException("월 머리글을 찾지 못했습니다.");
        }

        String publishedMonth = findLastCompleteMonth(sheet, monthColumns, readRegions(sheet));

        if (publishedMonth == null) {
            throw new VisitorStatsImportException(
                    "모든 시·군에 값이 있는 월이 없습니다. 공표 전 파일을 받았는지 확인하세요.");
        }

        List<VisitorStatsRow> rows = readRows(sheet, monthColumns.get(publishedMonth));

        if (rows.isEmpty()) {
            throw new VisitorStatsImportException(
                    "공표월 %s 에 값이 있는 관광지가 없습니다.".formatted(publishedMonth));
        }

        return new VisitorStatsWorkbook(publishedMonth, sourcePeriod(monthColumns), rows);
    }

    /** 열 위치를 찾기 전에 이 파일이 맞는지부터 확인한다. 다른 통계표를 적재하면 값이 뒤섞인다. */
    private static void validateFixedHeader(Sheet sheet) {
        Row header = sheet.getRow(HEADER_ROW);

        if (header == null) {
            throw new VisitorStatsImportException("머리글이 없습니다.");
        }

        expect(header, REGION_SIDO_COLUMN, "시도");
        expect(header, PLACE_COLUMN, "관광지");
        expect(header, VISITOR_KIND_COLUMN, "내/외국인");
    }

    private static void expect(Row header, int column, String expected) {
        String actual = stringValue(header.getCell(column));

        if (!expected.equals(actual)) {
            throw new VisitorStatsImportException(
                    "머리글이 다릅니다. %d번째 열에 '%s' 를 기대했으나 '%s' 입니다."
                            .formatted(column, expected, actual));
        }
    }

    /** @return 월(yyyyMM) → 열 번호. 머리글에 나온 순서를 유지한다. */
    private static Map<String, Integer> readMonthColumns(Sheet sheet) {
        Row monthHeader = sheet.getRow(MONTH_HEADER_ROW);

        if (monthHeader == null) {
            throw new VisitorStatsImportException("월 머리글 줄이 없습니다.");
        }

        Map<String, Integer> columns = new LinkedHashMap<>();

        for (int column = 0; column < monthHeader.getLastCellNum(); column++) {
            Matcher matcher = MONTH_HEADER.matcher(stringValue(monthHeader.getCell(column)));

            if (matcher.matches()) {
                columns.put("%s%02d".formatted(matcher.group(1), Integer.parseInt(matcher.group(2))),
                        column);
            }
        }

        return columns;
    }

    /** 파일에 나오는 시·군 전체. 커버리지 기준을 파일 안에서 얻어 DB 에 기대지 않는다. */
    private static Set<String> readRegions(Sheet sheet) {
        Set<String> regions = new LinkedHashSet<>();

        for (int rowIndex = FIRST_DATA_ROW; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            String region = stringValue(row.getCell(REGION_SIGUNGU_COLUMN));

            if (!region.isEmpty()) {
                regions.add(region);
            }
        }

        if (regions.isEmpty()) {
            throw new VisitorStatsImportException("시·군 값이 하나도 없습니다.");
        }

        return regions;
    }

    /**
     * 모든 시·군에 값이 있는 마지막 월.
     *
     * <p>0 은 공란이 아니라 실제 값이므로 값으로 센다. 어느 시·군이든 한 곳이라도 값이 있으면
     * 그 시·군은 그 달에 제출한 것으로 본다.
     */
    private static String findLastCompleteMonth(Sheet sheet, Map<String, Integer> monthColumns,
                                                Set<String> regions) {
        String lastComplete = null;

        for (Map.Entry<String, Integer> entry : monthColumns.entrySet()) {
            if (regionsWithValue(sheet, entry.getValue()).containsAll(regions)) {
                lastComplete = entry.getKey();
            }
        }

        return lastComplete;
    }

    private static Set<String> regionsWithValue(Sheet sheet, int column) {
        Set<String> regions = new HashSet<>();

        for (int rowIndex = FIRST_DATA_ROW; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);

            if (row != null && isNumber(row.getCell(column))) {
                regions.add(stringValue(row.getCell(REGION_SIGUNGU_COLUMN)));
            }
        }

        return regions;
    }

    private static List<VisitorStatsRow> readRows(Sheet sheet, int monthColumn) {
        List<VisitorStatsRow> rows = new ArrayList<>();

        for (int rowIndex = FIRST_DATA_ROW; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);

            if (row == null || !TOTAL_KIND.equals(stringValue(row.getCell(VISITOR_KIND_COLUMN)))) {
                continue;
            }

            Cell cell = row.getCell(monthColumn);

            // 공란은 그 달에 집계되지 않은 것이다. 0 으로 채우지 않고 행을 만들지 않는다.
            if (!isNumber(cell)) {
                continue;
            }

            String region = stringValue(row.getCell(REGION_SIGUNGU_COLUMN));
            String place = stringValue(row.getCell(PLACE_COLUMN));

            if (region.isEmpty() || place.isEmpty()) {
                throw new VisitorStatsImportException(
                        "%d번째 줄에 시·군명이나 관광지명이 없습니다.".formatted(rowIndex + 1));
            }

            rows.add(new VisitorStatsRow(region, place, Math.round(cell.getNumericCellValue())));
        }

        return rows;
    }

    private static String sourcePeriod(Map<String, Integer> monthColumns) {
        List<String> months = List.copyOf(monthColumns.keySet());
        return months.get(0) + "-" + months.get(months.size() - 1);
    }

    private static boolean isNumber(Cell cell) {
        return cell != null && cell.getCellType() == CellType.NUMERIC;
    }

    private static String stringValue(Cell cell) {
        if (cell == null || cell.getCellType() != CellType.STRING) {
            return "";
        }

        return cell.getStringCellValue().strip();
    }
}
