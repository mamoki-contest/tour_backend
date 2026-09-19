package com.mamoki.tour.domain.parking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.parking.importer.ParkingCatalogCsvParser;
import com.mamoki.tour.domain.parking.importer.ParkingCatalogImportException;
import com.mamoki.tour.domain.parking.importer.ParkingCatalogRow;

/**
 * 전국주차장정보표준데이터 판독기. {@code sample/} 의 실제 공식 파일로 검증한다.
 *
 * <p>파일은 CP949 이고 전국 18,883행이 한 덩어리로 온다. 우리가 쓰는 것은 제공기관명이
 * {@code 강원특별자치도 } 로 시작하는 1,398행뿐이다.
 */
class ParkingCatalogCsvParserTest {

    private static final Charset CP949 = Charset.forName("MS949");

    private static final String HEADER = String.join(",", ParkingCatalogCsvParser.REQUIRED_HEADERS);

    private static Path sampleFile() {
        return Path.of("sample", "전국주차장정보표준데이터.csv");
    }

    private static List<ParkingCatalogRow> parseSample() throws IOException {
        try (InputStream in = Files.newInputStream(sampleFile())) {
            return ParkingCatalogCsvParser.parse(in, "전국주차장정보표준데이터.csv");
        }
    }

    private static List<ParkingCatalogRow> parse(String csv) {
        return ParkingCatalogCsvParser.parse(
                new ByteArrayInputStream(csv.getBytes(CP949)), "테스트.csv");
    }

    /** 한 행을 만든다. 34열 중 우리가 읽는 열만 채우고 나머지는 비운다. */
    private static String row(Map<Integer, String> values) {
        String[] columns = new String[ParkingCatalogCsvParser.REQUIRED_HEADERS.size()];

        for (int i = 0; i < columns.length; i++) {
            columns[i] = values.getOrDefault(i, "");
        }

        return String.join(",", columns);
    }

    private static Map<Integer, String> gangwonRow() {
        return new java.util.LinkedHashMap<>(Map.ofEntries(
                Map.entry(0, "252-2-000101"),
                Map.entry(1, "중앙시장 제1공영주차장"),
                Map.entry(2, "공영"),
                Map.entry(3, "노외"),
                Map.entry(5, "강원특별자치도 강릉시 성남동 85-2"),
                Map.entry(6, "375"),
                Map.entry(9, "평일+토요일+공휴일"),
                Map.entry(10, "00:00"),
                Map.entry(11, "23:59"),
                Map.entry(16, "무료"),
                Map.entry(28, "37.75197184"),
                Map.entry(29, "128.899089"),
                Map.entry(31, "2026-04-15"),
                Map.entry(32, "4215000"),
                Map.entry(33, "강원특별자치도 강릉시")));
    }

    @Test
    @DisplayName("공식 파일에서 강원 행만 읽어낸다")
    void readsOnlyGangwonRows() throws IOException {
        List<ParkingCatalogRow> rows = parseSample();

        assertThat(rows).hasSize(1398);
        assertThat(rows).extracting(ParkingCatalogRow::providerName)
                .allSatisfy(name -> assertThat(name).startsWith("강원특별자치도 "));
    }

    @Test
    @DisplayName("강원 18개 시·군이 모두 들어 있다")
    void coversAllGangwonDistricts() throws IOException {
        Map<String, Long> byProvider = parseSample().stream()
                .collect(Collectors.groupingBy(ParkingCatalogRow::providerName,
                        Collectors.counting()));

        assertThat(byProvider).hasSize(18);
        assertThat(byProvider.get("강원특별자치도 강릉시")).isEqualTo(170);
    }

    @Test
    @DisplayName("값에 콤마가 든 열을 따옴표 규칙대로 읽는다")
    void honorsQuotedCommas() {
        Map<Integer, String> values = gangwonRow();
        values.put(1, "\"중앙시장 제1공영주차장(중앙시장, 남대천 둔치)\"");
        values.put(16, "\"유료, 일부 무료\"");

        List<ParkingCatalogRow> rows = parse(HEADER + "\n" + row(values));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).name()).isEqualTo("중앙시장 제1공영주차장(중앙시장, 남대천 둔치)");
        assertThat(rows.get(0).feeInfo()).isEqualTo("유료, 일부 무료");
    }

    @Test
    @DisplayName("좌표가 비어 있으면 0 이 아니라 null 로 남긴다")
    void keepsMissingCoordinatesNull() throws IOException {
        Map<Integer, String> values = gangwonRow();
        values.put(28, "");
        values.put(29, "");

        ParkingCatalogRow row = parse(HEADER + "\n" + row(values)).get(0);

        assertThat(row.latitude()).isNull();
        assertThat(row.longitude()).isNull();

        // 공식 파일의 강원 행에도 좌표가 빈 행이 있다. 버리지 않고 적재한다.
        assertThat(parseSample()).filteredOn(entry -> entry.latitude() == null).hasSize(4);
    }

    @Test
    @DisplayName("한 행의 모든 저장 항목을 읽는다")
    void readsEveryStoredField() throws IOException {
        ParkingCatalogRow row = byManagementNumber().get("252-2-000101");

        assertThat(row.name()).startsWith("중앙시장 제1공영주차장");
        assertThat(row.category()).isEqualTo("공영");
        assertThat(row.parkingType()).isEqualTo("노외");
        assertThat(row.lotAddress()).isEqualTo("강원특별자치도 강릉시 성남동 85-2");
        assertThat(row.capacity()).isEqualTo(375);
        assertThat(row.operatingDays()).isEqualTo("평일+토요일+공휴일");
        assertThat(row.weekdayOpenTime()).isNotBlank();
        assertThat(row.feeInfo()).isNotBlank();
        assertThat(row.latitude()).isEqualByComparingTo(new BigDecimal("37.75197184"));
        assertThat(row.longitude()).isEqualByComparingTo(new BigDecimal("128.899089"));
        assertThat(row.dataBaseDate()).isNotNull();
        assertThat(row.providerCode()).isNotBlank();
        assertThat(row.providerName()).isEqualTo("강원특별자치도 강릉시");
    }

    @Test
    @DisplayName("데이터기준일자는 날짜로 읽는다")
    void readsDataBaseDate() {
        ParkingCatalogRow row = parse(HEADER + "\n" + row(gangwonRow())).get(0);

        assertThat(row.dataBaseDate()).isEqualTo(LocalDate.of(2026, 4, 15));
    }

    @Test
    @DisplayName("헤더가 다르면 파일을 통째로 거부한다")
    void rejectsUnexpectedHeader() {
        assertThatThrownBy(() -> parse("주차장명,주차구획수\n가나다,10"))
                .isInstanceOf(ParkingCatalogImportException.class)
                .hasMessageContaining("헤더가 예상과 다릅니다");
    }

    @Test
    @DisplayName("빈 파일은 거부한다")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> parse(""))
                .isInstanceOf(ParkingCatalogImportException.class);
    }

    @Test
    @DisplayName("강원 행이 하나도 없으면 거부한다. 빈 스냅샷으로 교체하지 않는다")
    void rejectsFileWithoutGangwonRows() {
        Map<Integer, String> values = gangwonRow();
        values.put(33, "전남광주통합특별시 신안군");

        assertThatThrownBy(() -> parse(HEADER + "\n" + row(values)))
                .isInstanceOf(ParkingCatalogImportException.class)
                .hasMessageContaining("강원");
    }

    @Test
    @DisplayName("강원 행의 필수 값이 비면 파일을 거부한다")
    void rejectsGangwonRowWithMissingRequiredValue() {
        Map<Integer, String> values = gangwonRow();
        values.put(1, "");

        assertThatThrownBy(() -> parse(HEADER + "\n" + row(values)))
                .isInstanceOf(ParkingCatalogImportException.class)
                .hasMessageContaining("주차장명");
    }

    @Test
    @DisplayName("주차구획수가 숫자가 아니면 파일을 거부한다")
    void rejectsNonNumericCapacity() {
        Map<Integer, String> values = gangwonRow();
        values.put(6, "여러대");

        assertThatThrownBy(() -> parse(HEADER + "\n" + row(values)))
                .isInstanceOf(ParkingCatalogImportException.class)
                .hasMessageContaining("주차구획수");
    }

    @Test
    @DisplayName("강원 밖 행이 깨져 있어도 강원 행 적재를 막지 않는다")
    void ignoresBrokenRowsOutsideGangwon() {
        Map<Integer, String> broken = gangwonRow();
        broken.put(1, "");
        broken.put(6, "숫자아님");
        broken.put(33, "전남광주통합특별시 신안군");

        List<ParkingCatalogRow> rows =
                parse(HEADER + "\n" + row(broken) + "\n" + row(gangwonRow()));

        assertThat(rows).hasSize(1);
    }

    @Test
    @DisplayName("같은 주차장관리번호가 두 번 오면 파일을 거부한다")
    void rejectsDuplicateManagementNumber() {
        String csv = HEADER + "\n" + row(gangwonRow()) + "\n" + row(gangwonRow());

        assertThatThrownBy(() -> parse(csv))
                .isInstanceOf(ParkingCatalogImportException.class)
                .hasMessageContaining("주차장관리번호");
    }

    @Test
    @DisplayName("좌표가 한반도 밖이면 파일을 거부한다. 엉뚱한 곳의 주차장을 보여주지 않는다")
    void rejectsCoordinatesOutsideKorea() {
        Map<Integer, String> values = gangwonRow();
        values.put(28, "137.75197184");

        assertThatThrownBy(() -> parse(HEADER + "\n" + row(values)))
                .isInstanceOf(ParkingCatalogImportException.class)
                .hasMessageContaining("위도");
    }

    private static Map<String, ParkingCatalogRow> byManagementNumber() throws IOException {
        return parseSample().stream().collect(Collectors.toMap(
                ParkingCatalogRow::managementNumber, Function.identity()));
    }
}
