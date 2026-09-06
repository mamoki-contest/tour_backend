package com.mamoki.tour.domain.tmaprank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.tmaprank.importer.TmapRankCsvParser;
import com.mamoki.tour.domain.tmaprank.importer.TmapRankCsvRow;
import com.mamoki.tour.domain.tmaprank.importer.TmapRankImportException;

/** 저장한 실제 다운로드 파일로 TMAP 검색순위 CSV 계약을 검증한다. */
class TmapRankCsvParserTest {

    private static final String BOM = "\uFEFF";
    private static final String HEADER = "순위,관광지ID,관심지점명,구분,연령대,비율";

    private List<TmapRankCsvRow> parseFixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/fixtures/tmap-rank-gangneung-all-ages.csv")) {
            return TmapRankCsvParser.parse(in, "tmap-rank-gangneung-all-ages.csv");
        }
    }

    @Test
    @DisplayName("실제 다운로드 파일을 읽는다")
    void parsesRealFile() throws Exception {
        List<TmapRankCsvRow> rows = parseFixture();

        assertThat(rows).hasSize(5);

        TmapRankCsvRow first = rows.get(0);
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.dataLabId()).isEqualTo("1597d21403f63da1bb0539592597a525");
        assertThat(first.placeName()).isEqualTo("경포해변");
        assertThat(first.category()).isEqualTo("관광명소");
        assertThat(first.ageGroup()).isEqualTo("전체");
        assertThat(first.ratio()).isEqualByComparingTo(new BigDecimal("14.6"));
    }

    @Test
    @DisplayName("파일 앞 BOM 때문에 첫 컬럼을 놓치지 않는다")
    void stripsByteOrderMark() throws Exception {
        assertThat(parseFixture()).allSatisfy(row -> assertThat(row.rank()).isPositive());
    }

    @Test
    @DisplayName("행 끝 탭 문자를 걷어내고 비율을 읽는다")
    void handlesTrailingTab() throws Exception {
        assertThat(parseFixture())
                .extracting(TmapRankCsvRow::ratio)
                .containsExactly(
                        new BigDecimal("14.6"), new BigDecimal("12.6"),
                        new BigDecimal("9.5"), new BigDecimal("6.4"), new BigDecimal("6.1"));
    }

    @Test
    @DisplayName("전체 연령 파일을 구분한다")
    void identifiesAllAgesFile() throws Exception {
        assertThat(parseFixture()).allMatch(TmapRankCsvRow::isAllAges);
    }

    @Test
    @DisplayName("헤더가 다르면 파일을 거부한다")
    void rejectsUnexpectedHeader() {
        assertThatThrownBy(() -> parse(BOM + "순위,관광지명,비율\n1,경포해변,14.6\n"))
                .isInstanceOf(TmapRankImportException.class)
                .hasMessageContaining("헤더가 예상과 다릅니다");
    }

    @Test
    @DisplayName("헤더만 있고 데이터가 없으면 거부한다")
    void rejectsHeaderOnlyFile() {
        assertThatThrownBy(() -> parse(BOM + HEADER + "\n"))
                .isInstanceOf(TmapRankImportException.class)
                .hasMessageContaining("데이터 행이 없습니다");
    }

    @Test
    @DisplayName("빈 파일을 거부한다")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> parse(""))
                .isInstanceOf(TmapRankImportException.class)
                .hasMessageContaining("빈 파일");
    }

    @Test
    @DisplayName("필수 값이 비어 있으면 거부한다")
    void rejectsMissingRequiredValue() {
        assertThatThrownBy(() -> parse(BOM + HEADER + "\n1,,경포해변,관광명소,전체,14.6\t\n"))
                .isInstanceOf(TmapRankImportException.class)
                .hasMessageContaining("관광지ID");

        assertThatThrownBy(() -> parse(BOM + HEADER + "\n1,abc,,관광명소,전체,14.6\t\n"))
                .isInstanceOf(TmapRankImportException.class)
                .hasMessageContaining("관심지점명");
    }

    @Test
    @DisplayName("순위나 비율 형식이 어긋나면 거부한다")
    void rejectsMalformedNumbers() {
        assertThatThrownBy(() -> parse(BOM + HEADER + "\n일등,abc,경포해변,관광명소,전체,14.6\t\n"))
                .isInstanceOf(TmapRankImportException.class)
                .hasMessageContaining("순위");

        assertThatThrownBy(() -> parse(BOM + HEADER + "\n0,abc,경포해변,관광명소,전체,14.6\t\n"))
                .isInstanceOf(TmapRankImportException.class)
                .hasMessageContaining("순위");

        assertThatThrownBy(() -> parse(BOM + HEADER + "\n1,abc,경포해변,관광명소,전체,없음\t\n"))
                .isInstanceOf(TmapRankImportException.class)
                .hasMessageContaining("비율");

        assertThatThrownBy(() -> parse(BOM + HEADER + "\n1,abc,경포해변,관광명소,전체,-1\t\n"))
                .isInstanceOf(TmapRankImportException.class)
                .hasMessageContaining("비율");
    }

    private List<TmapRankCsvRow> parse(String content) {
        return TmapRankCsvParser.parse(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "테스트.csv");
    }
}
