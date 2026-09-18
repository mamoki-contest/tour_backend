package com.mamoki.tour.domain.visitorstats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.visitorstats.importer.VisitorStatsExcelParser;
import com.mamoki.tour.domain.visitorstats.importer.VisitorStatsImportException;
import com.mamoki.tour.domain.visitorstats.importer.VisitorStatsRow;
import com.mamoki.tour.domain.visitorstats.importer.VisitorStatsWorkbook;

/**
 * 관광지식정보시스템에서 실제로 내려받은 파일로 계약을 검증한다.
 *
 * <p>fixture 는 2026-09-18 에 강원특별자치도 전체, 2025-01~2026-06 기간으로 받은 원본이다.
 * 279곳이 들어 있고, 2026-04~06 열은 만들어져 있지만 비어 있으며, 2026-01~03 은 18개
 * 시·군 중 12개만 제출된 잠정 집계 상태다.
 */
class VisitorStatsExcelParserTest {

    private VisitorStatsWorkbook workbook;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = Files.newInputStream(sampleFile())) {
            workbook = VisitorStatsExcelParser.parse(in);
        }
    }


    /**
     * 공식 파일은 시트 하나에 강원 전체가 들어 있어 잘라낸 표본을 만들기 어렵다. TMAP zip 과
     * 같이 {@code sample/} 의 원본을 그대로 읽는다. 복사본을 테스트 자원에 또 두면 340KB 짜리
     * 같은 파일이 저장소에 두 번 들어간다.
     */
    private static Path sampleFile() {
        try (java.util.stream.Stream<Path> files = Files.list(Path.of("sample"))) {
            return files.filter(path -> path.getFileName().toString().startsWith("주요관광지점 입장객")
                            && path.getFileName().toString().endsWith(".xls"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "sample 에 입장객통계 파일이 없습니다. README 의 다운로드 조건을 참고하세요."));
        } catch (IOException e) {
            throw new IllegalStateException("sample 디렉터리를 읽지 못했습니다.", e);
        }
    }

    private Map<String, VisitorStatsRow> byPlace() {
        return workbook.rows().stream()
                .collect(Collectors.toMap(VisitorStatsRow::placeName, Function.identity()));
    }

    @Test
    @DisplayName("비어 있는 뒷 열을 공표월로 쓰지 않는다")
    void ignoresEmptyTrailingMonths() {
        // 파일에는 2026-06 까지 열이 있지만 2026-04 부터는 279곳 전부 비어 있다.
        assertThat(workbook.sourcePeriod()).isEqualTo("202501-202606");
        assertThat(workbook.publishedMonth()).isNotIn("202604", "202605", "202606");
    }

    @Test
    @DisplayName("일부 시·군만 제출된 달은 공표월로 쓰지 않는다")
    void ignoresMonthsMissingRegions() {
        // 2026-01~03 은 18개 시·군 중 12개만 있다. 그 달을 쓰면 강릉시·속초시 관광지가
        // 통째로 통계 없음으로 보인다.
        assertThat(workbook.publishedMonth()).isNotIn("202601", "202602", "202603");
    }

    @Test
    @DisplayName("모든 시·군에 값이 있는 마지막 월을 공표월로 본다")
    void usesLastCompleteMonth() {
        assertThat(workbook.publishedMonth()).isEqualTo("202512");
    }

    @Test
    @DisplayName("공표월에 값이 있는 관광지만 담는다")
    void keepsOnlyPlacesWithValue() {
        // 279곳 중 2025-12 에 값이 있는 곳은 254곳이다. 나머지는 미집계다.
        assertThat(workbook.rows()).hasSize(254);
    }

    @Test
    @DisplayName("합계 행만 읽는다. 내국인과 외국인을 우리가 다시 더하지 않는다")
    void readsOnlyTotalRows() {
        List<String> places = workbook.rows().stream().map(VisitorStatsRow::placeName).toList();

        // 관광지 하나가 내국인·외국인·합계로 세 번 오지만 결과에는 한 번만 담긴다.
        assertThat(places).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("시·군명을 함께 담아 동명 장소를 가를 수 있게 한다")
    void keepsRegionName() {
        assertThat(workbook.rows()).allSatisfy(row -> {
            assertThat(row.regionName()).isNotBlank();
            assertThat(row.placeName()).isNotBlank();
        });

        // 공표월은 모든 시·군이 갖춰진 달이므로 18개 시·군이 모두 나온다.
        assertThat(workbook.rows()).extracting(VisitorStatsRow::regionName)
                .contains("춘천시", "강릉시", "속초시", "원주시");
        assertThat(workbook.rows().stream().map(VisitorStatsRow::regionName).distinct().count())
                .isEqualTo(18);
    }

    @Test
    @DisplayName("0 명은 실제 값으로 담는다. 공란과 구분한다")
    void keepsRealZero() {
        List<VisitorStatsRow> zeros = workbook.rows().stream()
                .filter(row -> row.visitorCount() == 0L)
                .toList();

        // 공표월에 0 명으로 집계된 곳이 실제로 있다. 공란이었다면 행 자체가 없어야 한다.
        assertThat(zeros).isNotEmpty();
        assertThat(workbook.rows()).allSatisfy(row -> assertThat(row.visitorCount()).isNotNegative());
    }

    @Test
    @DisplayName("입장객 수를 파일 값 그대로 옮긴다")
    void keepsVisitorCountAsIs() {
        Map<String, VisitorStatsRow> rows = byPlace();

        assertThat(rows).containsKey("강촌레일파크");
        assertThat(rows.get("강촌레일파크").visitorCount()).isPositive();
        assertThat(rows.get("강촌레일파크").regionName()).isEqualTo("춘천시");
    }

    @Test
    @DisplayName("다른 통계표를 넣으면 머리글이 달라 거절한다")
    void rejectsWrongWorkbook() {
        assertThatThrownBy(() -> VisitorStatsExcelParser.parse(
                new java.io.ByteArrayInputStream("not an excel".getBytes())))
                .isInstanceOf(VisitorStatsImportException.class);
    }
}
