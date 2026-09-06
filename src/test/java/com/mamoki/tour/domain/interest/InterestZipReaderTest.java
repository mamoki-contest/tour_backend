package com.mamoki.tour.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mamoki.tour.domain.interest.importer.InterestCsvRow;
import com.mamoki.tour.domain.interest.importer.InterestImportException;
import com.mamoki.tour.domain.interest.importer.InterestZipContent;
import com.mamoki.tour.domain.interest.importer.InterestZipReader;

/** 저장소에 함께 추적하는 실제 다운로드 zip 으로 검증한다. */
class InterestZipReaderTest {

    private static final Path SAMPLE_DIR = Path.of("sample");
    private static final String HEADER = "\uFEFF순위,관광지ID,관심지점명,구분,연령대,비율";

    private Path realZip(String sigungu) throws IOException {
        try (var files = Files.list(SAMPLE_DIR)) {
            return files.filter(path -> path.getFileName().toString().contains(sigungu))
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Test
    @DisplayName("실제 다운로드 zip 을 읽는다")
    void readsRealZip() throws Exception {
        InterestZipContent content = InterestZipReader.read(realZip("강릉시"));

        assertThat(content.source().sigungu()).isEqualTo("강릉시");
        assertThat(content.source().sourcePeriod()).isEqualTo("202508-202607");
        assertThat(content.ageGroups()).containsExactlyInAnyOrder("전체", "20", "30", "40", "50", "60");
        assertThat(content.allAgesRows()).hasSize(30);
        assertThat(content.allAgesRows()).allMatch(InterestCsvRow::isAllAges);
    }

    @Test
    @DisplayName("18개 시·군 zip 이 모두 읽힌다")
    void readsAllSigunguZips() throws Exception {
        try (var files = Files.list(SAMPLE_DIR)) {
            List<Path> zips = files.filter(path -> path.getFileName().toString().endsWith(".zip")).toList();

            assertThat(zips).hasSize(18);
            assertThat(zips).allSatisfy(zip -> {
                InterestZipContent content = InterestZipReader.read(zip);
                assertThat(content.allAgesRows()).isNotEmpty();
                assertThat(content.ageGroups()).hasSize(6);
            });
        }
    }

    @Test
    @DisplayName("세대별 파일이 빠진 zip 은 거부한다")
    void rejectsIncompleteZip(@TempDir Path tempDir) throws Exception {
        Path zip = tempDir.resolve(
                "20260906205547_강원특별자치도+강릉시_202508-202607_데이터랩_다운로드.zip");
        writeZip(zip, List.of("전체", "20"));

        assertThatThrownBy(() -> InterestZipReader.read(zip))
                .isInstanceOf(InterestImportException.class)
                .hasMessageContaining("세대별 파일이 온전하지 않습니다");
    }

    @Test
    @DisplayName("CSV 가 없는 zip 은 거부한다")
    void rejectsZipWithoutCsv(@TempDir Path tempDir) throws Exception {
        Path zip = tempDir.resolve(
                "20260906205547_강원특별자치도+강릉시_202508-202607_데이터랩_다운로드.zip");
        writeZip(zip, List.of());

        assertThatThrownBy(() -> InterestZipReader.read(zip))
                .isInstanceOf(InterestImportException.class)
                .hasMessageContaining("CSV 가 없습니다");
    }

    @Test
    @DisplayName("한 파일에 연령대가 섞여 있으면 거부한다")
    void rejectsMixedAgeGroups(@TempDir Path tempDir) throws Exception {
        Path zip = tempDir.resolve(
                "20260906205547_강원특별자치도+강릉시_202508-202607_데이터랩_다운로드.zip");

        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("mixed.csv"));
            out.write((HEADER + "\n1,a,경포해변,관광명소,전체,14.6\t\n2,b,안목해변,관광명소,20,12.6\t\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        assertThatThrownBy(() -> InterestZipReader.read(zip))
                .isInstanceOf(InterestImportException.class)
                .hasMessageContaining("여러 연령대가 섞여");
    }

    private void writeZip(Path zip, List<String> ageGroups) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String ageGroup : ageGroups) {
                out.putNextEntry(new ZipEntry("인기관광지(" + ageGroup + ").csv"));
                out.write(csv(ageGroup).toByteArray());
                out.closeEntry();
            }
        }
    }

    private ByteArrayOutputStream csv(String ageGroup) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write((HEADER + "\n1,a,경포해변,관광명소," + ageGroup + ",14.6\t\n")
                .getBytes(StandardCharsets.UTF_8));
        return buffer;
    }
}
