package com.mamoki.tour.domain.attraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.attraction.dto.OnlineMentionView;
import com.mamoki.tour.domain.attraction.dto.TmapRankView;
import com.mamoki.tour.domain.attraction.dto.VisitorStatsView;
import com.mamoki.tour.domain.attraction.service.SignalLookupService;
import com.mamoki.tour.domain.mention.entity.OnlineMentionEntry;
import com.mamoki.tour.domain.mention.entity.OnlineMentionSnapshot;
import com.mamoki.tour.domain.mention.repository.OnlineMentionEntryRepository;
import com.mamoki.tour.domain.mention.repository.OnlineMentionSnapshotRepository;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankEntry;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankSnapshot;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankEntryRepository;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankSnapshotRepository;
import com.mamoki.tour.domain.visitorstats.entity.VisitorStatsEntry;
import com.mamoki.tour.domain.visitorstats.entity.VisitorStatsSnapshot;
import com.mamoki.tour.domain.visitorstats.repository.VisitorStatsEntryRepository;
import com.mamoki.tour.domain.visitorstats.repository.VisitorStatsSnapshotRepository;
import com.mamoki.tour.global.enums.CatalogMatchStatus;
import com.mamoki.tour.global.enums.MentionStatus;
import com.mamoki.tour.global.enums.SnapshotStatus;
import com.mamoki.tour.global.enums.TmapRankStatus;
import com.mamoki.tour.global.enums.VisitorCountStatus;

/**
 * 활성 스냅샷에서 관광지별 신호를 찾아오는 경로.
 *
 * <p>여기서 가려야 하는 것은 <b>"아직 적재하지 않았다"와 "적재했는데 그 장소가 없다"</b>의
 * 차이다. 둘을 같은 모양으로 돌려주면 위쪽에서 0 이나 최하 등급으로 채우게 되고, 값을 얻지
 * 못한 장소가 값이 낮은 장소로 둔갑한다. 그래서 스냅샷이 없을 때와 항목이 없을 때의 반환이
 * 서로 다르다.
 */
class SignalLookupServiceTest {

    private static final LocalDateTime COLLECTED_AT = LocalDateTime.of(2026, 9, 10, 12, 0);
    private static final List<String> CONTENT_IDS = List.of("126508", "126509");

    private SignalLookupService signalLookupService;

    private OnlineMentionSnapshotRepository mentionSnapshotRepository;
    private OnlineMentionEntryRepository mentionEntryRepository;
    private TmapRankSnapshotRepository tmapSnapshotRepository;
    private TmapRankEntryRepository tmapEntryRepository;
    private VisitorStatsSnapshotRepository visitorStatsSnapshotRepository;
    private VisitorStatsEntryRepository visitorStatsEntryRepository;

    @BeforeEach
    void setUp() {
        mentionSnapshotRepository = Mockito.mock(OnlineMentionSnapshotRepository.class);
        mentionEntryRepository = Mockito.mock(OnlineMentionEntryRepository.class);
        tmapSnapshotRepository = Mockito.mock(TmapRankSnapshotRepository.class);
        tmapEntryRepository = Mockito.mock(TmapRankEntryRepository.class);
        visitorStatsSnapshotRepository = Mockito.mock(VisitorStatsSnapshotRepository.class);
        visitorStatsEntryRepository = Mockito.mock(VisitorStatsEntryRepository.class);

        signalLookupService = new SignalLookupService(
                mentionSnapshotRepository, mentionEntryRepository,
                tmapSnapshotRepository, tmapEntryRepository,
                visitorStatsSnapshotRepository, visitorStatsEntryRepository);

        given(mentionSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .willReturn(Optional.empty());
        given(tmapSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .willReturn(Optional.empty());
        given(visitorStatsSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .willReturn(Optional.empty());
    }

    // --- 온라인 언급량 ---------------------------------------------------------

    @Test
    @DisplayName("언급량 스냅샷이 없으면 빈 값으로 알린다")
    void mentionsAreAbsentWithoutSnapshot() {
        assertThat(signalLookupService.findOnlineMentions(CONTENT_IDS)).isEmpty();
        verifyNoInteractions(mentionEntryRepository);
    }

    @Test
    @DisplayName("언급량 스냅샷의 값을 규칙 버전과 함께 돌려준다")
    void findsMentionsWithRuleVersion() {
        OnlineMentionSnapshot snapshot = mentionSnapshot("name+sigungu");
        given(mentionSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .willReturn(Optional.of(snapshot));
        given(mentionEntryRepository.findSortableByContentIds(snapshot, CONTENT_IDS))
                .willReturn(List.of(mentionEntry(snapshot, "126508", 140_006L)));

        Map<String, OnlineMentionView> views =
                signalLookupService.findOnlineMentions(CONTENT_IDS).orElseThrow();

        OnlineMentionView view = views.get("126508");
        assertThat(view.status()).isEqualTo(MentionStatus.COLLECTED);
        assertThat(view.count()).isEqualTo(140_006L);
        assertThat(view.collectedAt()).isEqualTo(COLLECTED_AT);
        assertThat(view.ruleVersion()).isEqualTo("name+sigungu");
    }

    @Test
    @DisplayName("스냅샷에 없는 장소는 값을 만들어 채우지 않는다")
    void missingPlaceIsNotFilledIn() {
        OnlineMentionSnapshot snapshot = mentionSnapshot("name+sigungu");
        given(mentionSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .willReturn(Optional.of(snapshot));
        given(mentionEntryRepository.findSortableByContentIds(snapshot, CONTENT_IDS))
                .willReturn(List.of(mentionEntry(snapshot, "126508", 140_006L)));

        Map<String, OnlineMentionView> views =
                signalLookupService.findOnlineMentions(CONTENT_IDS).orElseThrow();

        // 수집됐는데 0 인 것이 아니라 정렬 대상이 아닌 것이다. 위쪽에서 상태로 채운다.
        assertThat(views).containsOnlyKeys("126508");
        assertThat(views.get("126509")).isNull();
    }

    @Test
    @DisplayName("조회할 장소가 없으면 스냅샷만 확인하고 항목은 찾지 않는다")
    void emptyContentIdsSkipMentionEntryLookup() {
        given(mentionSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .willReturn(Optional.of(mentionSnapshot("name+sigungu")));

        assertThat(signalLookupService.findOnlineMentions(List.of())).contains(Map.of());
        verifyNoInteractions(mentionEntryRepository);
    }

    @Test
    @DisplayName("규칙 버전은 활성 스냅샷에서 읽고, 스냅샷이 없으면 비어 있다")
    void findsRuleVersion() {
        assertThat(signalLookupService.findMentionRuleVersion()).isEmpty();

        given(mentionSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .willReturn(Optional.of(mentionSnapshot("name+sigungu+suffix")));

        assertThat(signalLookupService.findMentionRuleVersion()).contains("name+sigungu+suffix");
    }

    // --- TMAP 검색순위 ---------------------------------------------------------

    @Test
    @DisplayName("TMAP 스냅샷이 없으면 빈 맵이다")
    void tmapRanksAreEmptyWithoutSnapshot() {
        assertThat(signalLookupService.findTmapRanks(CONTENT_IDS)).isEmpty();
        verifyNoInteractions(tmapEntryRepository);
    }

    @Test
    @DisplayName("확정 매칭된 장소의 순위를 조회기간과 함께 돌려준다")
    void findsTmapRanks() {
        TmapRankSnapshot snapshot = tmapSnapshot("202508-202607");
        given(tmapSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .willReturn(Optional.of(snapshot));
        given(tmapEntryRepository.findMatchedByContentIds(snapshot, CONTENT_IDS))
                .willReturn(List.of(tmapEntry(snapshot, "126508", 2)));

        Map<String, TmapRankView> views = signalLookupService.findTmapRanks(CONTENT_IDS);

        assertThat(views).containsOnlyKeys("126508");
        assertThat(views.get("126508").status()).isEqualTo(TmapRankStatus.AVAILABLE);
        assertThat(views.get("126508").rank()).isEqualTo(2);
        assertThat(views.get("126508").period()).isEqualTo("202508-202607");
    }

    @Test
    @DisplayName("조회할 장소가 없으면 TMAP 항목도 찾지 않는다")
    void emptyContentIdsSkipTmapEntryLookup() {
        given(tmapSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .willReturn(Optional.of(tmapSnapshot("202508-202607")));

        assertThat(signalLookupService.findTmapRanks(List.of())).isEmpty();
        verifyNoInteractions(tmapEntryRepository);
    }

    // --- 입장객 통계 -----------------------------------------------------------

    @Test
    @DisplayName("입장객 스냅샷이 없으면 빈 값으로 알린다")
    void visitorStatsAreAbsentWithoutSnapshot() {
        assertThat(signalLookupService.findVisitorStats(CONTENT_IDS)).isEmpty();
        verifyNoInteractions(visitorStatsEntryRepository);
    }

    @Test
    @DisplayName("적재했지만 조회할 장소가 없으면 빈 맵이다")
    void importedButNoContentIds() {
        given(visitorStatsSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .willReturn(Optional.of(visitorStatsSnapshot()));

        // 빈 값(아직 적재 안 함)과 빈 맵(적재했는데 대상이 없음)은 다른 뜻이다.
        assertThat(signalLookupService.findVisitorStats(List.of())).contains(Map.of());
        verifyNoInteractions(visitorStatsEntryRepository);
    }

    @Test
    @DisplayName("공표월 입장객 수를 잠정·확정 구분과 함께 돌려준다")
    void findsVisitorStats() {
        VisitorStatsSnapshot snapshot = visitorStatsSnapshot();
        given(visitorStatsSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .willReturn(Optional.of(snapshot));
        given(visitorStatsEntryRepository.findMatchedByContentIds(snapshot, CONTENT_IDS))
                .willReturn(List.of(visitorStatsEntry(snapshot, "126508", 52_341L)));

        Map<String, VisitorStatsView> views =
                signalLookupService.findVisitorStats(CONTENT_IDS).orElseThrow();

        assertThat(views).containsOnlyKeys("126508");
        assertThat(views.get("126508").status()).isEqualTo(VisitorStatsView.Status.AVAILABLE);
        assertThat(views.get("126508").count()).isEqualTo(52_341L);
        assertThat(views.get("126508").period()).isEqualTo("202603");
        assertThat(views.get("126508").countStatus()).isEqualTo(VisitorCountStatus.PROVISIONAL.name());
    }

    @Test
    @DisplayName("세 신호는 서로 독립이라 하나가 비어도 나머지는 그대로다")
    void signalsAreIndependent() {
        TmapRankSnapshot snapshot = tmapSnapshot("202508-202607");
        given(tmapSnapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .willReturn(Optional.of(snapshot));
        given(tmapEntryRepository.findMatchedByContentIds(any(), any()))
                .willReturn(List.of(tmapEntry(snapshot, "126508", 2)));

        assertThat(signalLookupService.findOnlineMentions(CONTENT_IDS)).isEmpty();
        assertThat(signalLookupService.findVisitorStats(CONTENT_IDS)).isEmpty();
        assertThat(signalLookupService.findTmapRanks(CONTENT_IDS)).containsOnlyKeys("126508");
    }

    // --- 도우미 ---------------------------------------------------------------

    private static OnlineMentionSnapshot mentionSnapshot(String queryRuleVersion) {
        return OnlineMentionSnapshot.builder()
                .version("202609-1")
                .collectionMonth("202609")
                .queryRuleVersion(queryRuleVersion)
                .startedAt(COLLECTED_AT)
                .status(SnapshotStatus.ACTIVE)
                .targetCount(2)
                .build();
    }

    private static OnlineMentionEntry mentionEntry(OnlineMentionSnapshot snapshot,
                                                   String contentId, Long total) {
        return OnlineMentionEntry.builder()
                .snapshot(snapshot)
                .contentId(contentId)
                .placeName("경포해변")
                .searchQuery("경포해변 강릉")
                .mentionTotal(total)
                .status(MentionStatus.COLLECTED)
                .collectedAt(COLLECTED_AT)
                .build();
    }

    private static TmapRankSnapshot tmapSnapshot(String sourcePeriod) {
        return TmapRankSnapshot.builder()
                .version("202609-1")
                .sourcePeriod(sourcePeriod)
                .downloadedOn(LocalDate.of(2026, 9, 6))
                .importedAt(COLLECTED_AT)
                .status(SnapshotStatus.ACTIVE)
                .sourceFileName("tmap.zip")
                .rowCount(2)
                .build();
    }

    private static TmapRankEntry tmapEntry(TmapRankSnapshot snapshot, String contentId, int rank) {
        return TmapRankEntry.builder()
                .snapshot(snapshot)
                .rawRegionName("강릉시")
                .rawPlaceName("경포해변")
                .normalizedName("경포해변")
                .searchRatio(new BigDecimal("12.3456"))
                .sourceRank(rank)
                .contentId(contentId)
                .matchStatus(CatalogMatchStatus.MATCHED)
                .build();
    }

    private static VisitorStatsSnapshot visitorStatsSnapshot() {
        return VisitorStatsSnapshot.builder()
                .version("202603-1")
                .publishedMonth("202603")
                .sourcePeriod("200407-202603")
                .countStatus(VisitorCountStatus.PROVISIONAL)
                .downloadedOn(LocalDate.of(2026, 9, 18))
                .importedAt(COLLECTED_AT)
                .status(SnapshotStatus.ACTIVE)
                .sourceFileName("visitor-stats.xls")
                .rowCount(2)
                .build();
    }

    private static VisitorStatsEntry visitorStatsEntry(VisitorStatsSnapshot snapshot,
                                                       String contentId, long count) {
        return VisitorStatsEntry.builder()
                .snapshot(snapshot)
                .rawRegionName("강릉시")
                .rawPlaceName("경포해변")
                .normalizedName("경포해변")
                .visitorCount(count)
                .contentId(contentId)
                .matchStatus(CatalogMatchStatus.MATCHED)
                .build();
    }
}
