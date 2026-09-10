package com.mamoki.tour.domain.mention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.mention.entity.OnlineMentionEntry;
import com.mamoki.tour.domain.mention.repository.OnlineMentionEntryRepository;
import com.mamoki.tour.domain.mention.repository.OnlineMentionSnapshotRepository;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollectResult;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollector;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankEntry;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankSnapshot;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankEntryRepository;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankSnapshotRepository;
import com.mamoki.tour.global.enums.CatalogMatchStatus;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.enums.MentionStatus;
import com.mamoki.tour.global.enums.SnapshotStatus;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.infra.naver.NaverAuthenticationException;
import com.mamoki.tour.infra.naver.NaverBlogSearchClient;
import com.mamoki.tour.infra.naver.NaverRateLimitException;
import com.mamoki.tour.infra.naver.dto.NaverBlogSearchResponse;

@SpringBootTest
@ActiveProfiles("test")
class OnlineMentionCollectorTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 9);

    @Autowired
    private OnlineMentionCollector collector;

    @Autowired
    private OnlineMentionSnapshotRepository snapshotRepository;

    @Autowired
    private OnlineMentionEntryRepository entryRepository;

    @Autowired
    private AttractionRepository attractionRepository;

    @Autowired
    private RegionCodeRepository regionCodeRepository;

    @Autowired
    private TmapRankSnapshotRepository tmapSnapshotRepository;

    @Autowired
    private TmapRankEntryRepository tmapEntryRepository;

    @MockitoBean
    private NaverBlogSearchClient searchClient;

    @BeforeEach
    void reset() {
        entryRepository.deleteAllInBatch();
        snapshotRepository.deleteAllInBatch();
        tmapEntryRepository.deleteAllInBatch();
        tmapSnapshotRepository.deleteAllInBatch();
        attractionRepository.deleteAllInBatch();
    }

    /** 활성 TMAP 스냅샷에 확정 매칭된 관광지를 하나 올려 둔다. */
    private void saveActiveTmapRank(String contentId, String placeName) {
        TmapRankSnapshot snapshot = tmapSnapshotRepository.save(TmapRankSnapshot.builder()
                .version("tmap-" + contentId)
                .sourcePeriod("202508-202607")
                .downloadedOn(LocalDate.now())
                .importedAt(LocalDateTime.now())
                .status(SnapshotStatus.ACTIVE)
                .sourceFileName("test.zip")
                .rowCount(1)
                .build());

        tmapEntryRepository.save(TmapRankEntry.builder()
                .snapshot(snapshot)
                .rawRegionName("정선군")
                .rawPlaceName(placeName)
                .normalizedName(placeName)
                .searchRatio(new BigDecimal("1.234"))
                .sourceRank(3)
                .contentId(contentId)
                .matchStatus(CatalogMatchStatus.MATCHED)
                .build());
    }

    private Attraction save(String contentId, String name, String lawdCode) {
        RegionCode region = regionCodeRepository.findByLawdCode(lawdCode).orElseThrow();
        return attractionRepository.save(Attraction.builder()
                .contentId(contentId)
                .name(name)
                .regionCode(region)
                .dataStatus(DataStatus.AVAILABLE)
                .baseAt(LocalDateTime.now())
                .source("KorService2")
                .build());
    }

    private NaverBlogSearchResponse response(long total) {
        return new NaverBlogSearchResponse("now", total, 1, 1, List.of());
    }

    @Test
    @DisplayName("카탈로그를 순회해 언급량을 모으고 스냅샷을 활성화한다")
    void collectsAndActivates() {
        save("1", "경포해변", "51150");
        save("2", "속초해변", "51210");
        given(searchClient.search(anyString())).willReturn(response(1000L));

        OnlineMentionCollectResult result = collector.collect(MONTH);

        assertThat(result.target()).isEqualTo(2);
        assertThat(result.collected()).isEqualTo(2);
        assertThat(result.snapshot().getStatus()).isEqualTo(SnapshotStatus.ACTIVE);
        assertThat(result.snapshot().getCollectionMonth()).isEqualTo("202609");
        assertThat(result.snapshot().getQueryRuleVersion()).isNotBlank();
    }

    @Test
    @DisplayName("실제 사용한 검색어와 값을 함께 남긴다")
    void keepsQueryAndTotal() {
        save("1", "경포해변", "51150");
        given(searchClient.search(anyString())).willReturn(response(140006L));

        collector.collect(MONTH);

        assertThat(entryRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getPlaceName()).isEqualTo("경포해변");
            assertThat(entry.getSearchQuery()).contains("경포해변").contains("강릉시");
            assertThat(entry.getMentionTotal()).isEqualTo(140006L);
            assertThat(entry.getStatus()).isEqualTo(MentionStatus.COLLECTED);
            assertThat(entry.isSortable()).isTrue();
        });
    }

    @Test
    @DisplayName("정상 0건은 수집 성공으로 다룬다")
    void zeroTotalIsStillCollected() {
        save("1", "이름없는어느곳", "51150");
        given(searchClient.search(anyString())).willReturn(response(0L));

        OnlineMentionCollectResult result = collector.collect(MONTH);

        assertThat(result.collected()).isEqualTo(1);
        assertThat(entryRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getStatus()).isEqualTo(MentionStatus.COLLECTED);
            assertThat(entry.getMentionTotal()).isZero();
            assertThat(entry.isSortable()).isTrue();
        });
    }

    @Test
    @DisplayName("TMAP 순위에 있는데 0건이면 모호로 표시하고 정렬에서 뺀다")
    void marksVerifiedZeroAsAmbiguous() {
        save("1", "강원랜드카지노", "51770");
        saveActiveTmapRank("1", "강원랜드카지노");
        given(searchClient.search(anyString())).willReturn(response(0L));

        OnlineMentionCollectResult result = collector.collect(MONTH);

        assertThat(result.ambiguous()).isEqualTo(1);
        assertThat(result.collected()).isZero();
        assertThat(entryRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getStatus()).isEqualTo(MentionStatus.AMBIGUOUS);
            assertThat(entry.isSortable()).isFalse();
            assertThat(entry.getNote()).contains("TMAP");
        });
    }

    @Test
    @DisplayName("TMAP 순위에 있어도 0건이 아니면 그대로 수집한다")
    void keepsNonZeroTotalForRankedPlace() {
        save("1", "경포해변", "51150");
        saveActiveTmapRank("1", "경포해변");
        given(searchClient.search(anyString())).willReturn(response(140006L));

        OnlineMentionCollectResult result = collector.collect(MONTH);

        assertThat(result.collected()).isEqualTo(1);
        assertThat(entryRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getStatus()).isEqualTo(MentionStatus.COLLECTED);
            assertThat(entry.getMentionTotal()).isEqualTo(140006L);
        });
    }

    @Test
    @DisplayName("TMAP 순위에 없는 0건은 실제로 언급이 적은 것으로 두고 정렬에 쓴다")
    void keepsUnrankedZeroSortable() {
        save("1", "이름없는어느곳", "51150");
        saveActiveTmapRank("2", "다른곳");
        given(searchClient.search(anyString())).willReturn(response(0L));

        OnlineMentionCollectResult result = collector.collect(MONTH);

        assertThat(result.collected()).isEqualTo(1);
        assertThat(entryRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getStatus()).isEqualTo(MentionStatus.COLLECTED);
            assertThat(entry.getMentionTotal()).isZero();
            assertThat(entry.isSortable()).isTrue();
        });
    }

    @Test
    @DisplayName("카탈로그에 같은 이름이 여럿이면 모호로 표시하고 정렬에서 뺀다")
    void marksDuplicateNamesAmbiguous() {
        save("1", "해수욕장", "51150");
        save("2", "해수욕장", "51210");
        given(searchClient.search(anyString())).willReturn(response(500L));

        OnlineMentionCollectResult result = collector.collect(MONTH);

        assertThat(result.ambiguous()).isEqualTo(2);
        assertThat(result.collected()).isZero();
        assertThat(entryRepository.findAll()).allSatisfy(entry -> {
            assertThat(entry.getStatus()).isEqualTo(MentionStatus.AMBIGUOUS);
            assertThat(entry.getMentionTotal()).isNull();
            assertThat(entry.isSortable()).isFalse();
            assertThat(entry.getNote()).contains("같은 이름이 2건");
        });
    }

    @Test
    @DisplayName("인증에 실패하면 즉시 멈추고 스냅샷을 활성화하지 않는다")
    void abortsOnAuthenticationFailure() {
        save("1", "경포해변", "51150");
        willThrow(new NaverAuthenticationException("인증 실패"))
                .given(searchClient).search(anyString());

        assertThatThrownBy(() -> collector.collect(MONTH))
                .isInstanceOf(NaverAuthenticationException.class);

        assertThat(snapshotRepository.findByStatus(SnapshotStatus.ACTIVE)).isEmpty();
        assertThat(snapshotRepository.findAll()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getStatus()).isEqualTo(SnapshotStatus.FAILED);
            assertThat(snapshot.getFailureReason()).contains("인증 실패");
        });
    }

    @Test
    @DisplayName("한도를 초과하면 즉시 멈춘다")
    void abortsOnRateLimit() {
        save("1", "경포해변", "51150");
        willThrow(new NaverRateLimitException("한도 초과"))
                .given(searchClient).search(anyString());

        assertThatThrownBy(() -> collector.collect(MONTH))
                .isInstanceOf(NaverRateLimitException.class);

        assertThat(snapshotRepository.findByStatus(SnapshotStatus.ACTIVE)).isEmpty();
    }

    @Test
    @DisplayName("일부 호출이 끝내 실패하면 부분 수집분을 남기지 않고 직전 스냅샷을 지킨다")
    void keepsPreviousSnapshotOnPartialFailure() {
        save("1", "경포해변", "51150");
        given(searchClient.search(anyString())).willReturn(response(100L));
        OnlineMentionCollectResult previous = collector.collect(MONTH);

        save("2", "속초해변", "51210");
        willThrow(new ExternalApiException(ApiProvider.NAVER_BLOG_SEARCH, "일시 오류"))
                .given(searchClient).search(anyString());

        assertThatThrownBy(() -> collector.collect(MONTH))
                .isInstanceOf(ExternalApiException.class);

        assertThat(snapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .get()
                .extracting(snapshot -> snapshot.getId())
                .isEqualTo(previous.snapshot().getId());
        assertThat(entryRepository.findAll())
                .allSatisfy(entry -> assertThat(entry.getSnapshot().getId())
                        .isEqualTo(previous.snapshot().getId()));
    }

    @Test
    @DisplayName("일시적 실패는 재시도 후 성공하면 정상 수집으로 남는다")
    void retriesTransientFailure() {
        save("1", "경포해변", "51150");
        given(searchClient.search(anyString()))
                .willThrow(new ExternalApiException(ApiProvider.NAVER_BLOG_SEARCH, "일시 오류"))
                .willReturn(response(777L));

        OnlineMentionCollectResult result = collector.collect(MONTH);

        assertThat(result.collected()).isEqualTo(1);
        assertThat(entryRepository.findAll()).singleElement()
                .extracting(OnlineMentionEntry::getMentionTotal)
                .isEqualTo(777L);
    }

    @Test
    @DisplayName("다시 수집하면 직전 스냅샷은 물러나고 활성은 하나만 남는다")
    void replacesActiveSnapshot() {
        save("1", "경포해변", "51150");
        given(searchClient.search(anyString())).willReturn(response(100L));

        OnlineMentionCollectResult first = collector.collect(MONTH);
        OnlineMentionCollectResult second = collector.collect(MONTH);

        assertThat(snapshotRepository.findByVersion(first.snapshot().getVersion()))
                .get()
                .extracting(snapshot -> snapshot.getStatus())
                .isEqualTo(SnapshotStatus.SUPERSEDED);
        assertThat(snapshotRepository.findByStatus(SnapshotStatus.ACTIVE))
                .get()
                .extracting(snapshot -> snapshot.getId())
                .isEqualTo(second.snapshot().getId());
    }

    @Test
    @DisplayName("카탈로그가 비어 있으면 수집하지 않는다")
    void rejectsEmptyCatalog() {
        assertThatThrownBy(() -> collector.collect(MONTH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("카탈로그가 비어 있어");

        assertThat(snapshotRepository.findAll()).isEmpty();
    }
}
