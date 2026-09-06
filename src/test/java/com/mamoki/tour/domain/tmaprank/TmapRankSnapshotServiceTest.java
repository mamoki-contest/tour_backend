package com.mamoki.tour.domain.tmaprank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.tmaprank.entity.TmapRankEntry;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankSnapshot;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankEntryRepository;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankSnapshotRepository;
import com.mamoki.tour.domain.tmaprank.service.TmapRankSnapshotService;
import com.mamoki.tour.global.enums.CatalogMatchStatus;
import com.mamoki.tour.global.enums.SnapshotStatus;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TmapRankSnapshotServiceTest {

    @Autowired
    private TmapRankSnapshotService snapshotService;

    @Autowired
    private TmapRankSnapshotRepository snapshotRepository;

    @Autowired
    private TmapRankEntryRepository tmapRankEntryRepository;

    @Test
    @DisplayName("첫 스냅샷을 활성화하면 조회에 사용된다")
    void activatesFirstSnapshot() {
        TmapRankSnapshot snapshot = importing("v202507", "202507");
        addRow(snapshot, "경포해수욕장", "126508");

        snapshotService.activate(snapshot);

        assertThat(snapshotService.findActive()).contains(snapshot);
        assertThat(snapshot.getRowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("새 스냅샷을 활성화하면 직전 스냅샷은 물러나고 활성은 하나만 남는다")
    void replacesActiveSnapshotAtomically() {
        TmapRankSnapshot previous = importing("v202506", "202506");
        addRow(previous, "경포해수욕장", "126508");
        snapshotService.activate(previous);

        TmapRankSnapshot next = importing("v202507", "202507");
        addRow(next, "경포해수욕장", "126508");
        snapshotService.activate(next);

        assertThat(previous.getStatus()).isEqualTo(SnapshotStatus.SUPERSEDED);
        assertThat(snapshotService.findActive()).contains(next);
        assertThat(snapshotRepository.findAll())
                .filteredOn(TmapRankSnapshot::isActive)
                .hasSize(1);
    }

    @Test
    @DisplayName("적재 실패는 이력으로 남고 직전 활성 스냅샷은 그대로 유지된다")
    void failureKeepsPreviousActiveSnapshot() {
        TmapRankSnapshot previous = importing("v202506", "202506");
        addRow(previous, "경포해수욕장", "126508");
        snapshotService.activate(previous);

        TmapRankSnapshot broken = importing("v202507", "202507");
        addRow(broken, "깨진 행", "999999");

        snapshotService.markFailed(broken, "필수 헤더 누락: 관광지명");

        assertThat(broken.getStatus()).isEqualTo(SnapshotStatus.FAILED);
        assertThat(broken.getFailureReason()).contains("필수 헤더 누락");
        assertThat(snapshotService.findActive()).contains(previous);
        assertThat(previous.isActive()).isTrue();
    }

    @Test
    @DisplayName("실패한 스냅샷의 부분 적재 행은 남기지 않는다")
    void failureRemovesPartiallyImportedRows() {
        TmapRankSnapshot broken = importing("v202507", "202507");
        addRow(broken, "부분 적재 행", "111111");

        snapshotService.markFailed(broken, "부분 파일");

        assertThat(tmapRankEntryRepository.countBySnapshot(broken)).isZero();
    }

    @Test
    @DisplayName("행이 하나도 없는 스냅샷은 활성화하지 않는다")
    void rejectsEmptySnapshot() {
        TmapRankSnapshot empty = importing("v202507", "202507");

        assertThatThrownBy(() -> snapshotService.activate(empty))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("적재된 순위 행이 없어");
    }

    @Test
    @DisplayName("적재 이력은 최신순으로 조회된다")
    void listsImportHistoryNewestFirst() {
        TmapRankSnapshot older = importing("v202505", "202505");
        TmapRankSnapshot newer = importing("v202507", "202507");

        assertThat(snapshotService.findImportHistory())
                .extracting(TmapRankSnapshot::getVersion)
                .containsSubsequence(newer.getVersion(), older.getVersion());
    }

    private TmapRankSnapshot importing(String version, String period) {
        return snapshotRepository.save(TmapRankSnapshot.builder()
                .version(version)
                .sourcePeriod(period)
                .downloadedOn(LocalDate.of(2026, 9, 6))
                .importedAt(LocalDateTime.now().plusSeconds(version.hashCode() % 100))
                .status(SnapshotStatus.IMPORTING)
                .sourceFileName(version + ".csv")
                .rowCount(0)
                .build());
    }

    private void addRow(TmapRankSnapshot snapshot, String placeName, String contentId) {
        tmapRankEntryRepository.save(TmapRankEntry.builder()
                .snapshot(snapshot)
                .rawRegionName("강릉시")
                .rawPlaceName(placeName)
                .normalizedName(placeName.replace(" ", ""))
                .searchRatio(new BigDecimal("1000"))
                .sourceRank(1)
                .contentId(contentId)
                .matchStatus(CatalogMatchStatus.MATCHED)
                .build());
    }
}
