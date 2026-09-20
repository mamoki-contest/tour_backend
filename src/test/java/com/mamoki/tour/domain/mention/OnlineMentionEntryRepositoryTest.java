package com.mamoki.tour.domain.mention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.mention.entity.OnlineMentionEntry;
import com.mamoki.tour.domain.mention.entity.OnlineMentionSnapshot;
import com.mamoki.tour.domain.mention.repository.OnlineMentionEntryRepository;
import com.mamoki.tour.domain.mention.repository.OnlineMentionSnapshotRepository;
import com.mamoki.tour.global.enums.MentionStatus;
import com.mamoki.tour.global.enums.SnapshotStatus;

/**
 * 스냅샷 안의 언급량 항목을 조회하는 질의.
 *
 * <p>여기서 상태로 걸러내면 그 위 어디에서도 "왜 값이 없는지"를 되살릴 수 없다(#94).
 * 조회에 담기지 않은 장소는 모두 같은 모양이 되어, 이름이 모호해 뺀 장소와 호출이 실패한
 * 장소가 한 이름으로 합쳐진다. 그래서 이 질의는 상태를 보지 않고 스냅샷에 담긴 것을
 * 그대로 올려보낸다. 정렬에 쓸 수 있는지는 상태를 받아 본 쪽이 가린다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OnlineMentionEntryRepositoryTest {

    private static final LocalDateTime COLLECTED_AT = LocalDateTime.of(2026, 9, 10, 3, 0);

    @Autowired
    private OnlineMentionEntryRepository entryRepository;

    @Autowired
    private OnlineMentionSnapshotRepository snapshotRepository;

    private OnlineMentionSnapshot snapshot;

    @BeforeEach
    void setUp() {
        entryRepository.deleteAllInBatch();
        snapshotRepository.deleteAllInBatch();

        snapshot = snapshotRepository.save(snapshot("202609-1", SnapshotStatus.ACTIVE));
    }

    @Test
    @DisplayName("스냅샷에 담긴 항목은 상태와 무관하게 모두 조회된다")
    void findsEveryStatus() {
        entryRepository.saveAll(List.of(
                entry("1", MentionStatus.COLLECTED, 140_006L),
                entry("2", MentionStatus.AMBIGUOUS, null),
                entry("3", MentionStatus.UNAVAILABLE, null),
                entry("4", MentionStatus.COLLECTION_FAILED, null)));

        List<OnlineMentionEntry> found =
                entryRepository.findByContentIds(snapshot, List.of("1", "2", "3", "4"));

        assertThat(found).extracting(OnlineMentionEntry::getContentId, OnlineMentionEntry::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("1", MentionStatus.COLLECTED),
                        tuple("2", MentionStatus.AMBIGUOUS),
                        tuple("3", MentionStatus.UNAVAILABLE),
                        tuple("4", MentionStatus.COLLECTION_FAILED));
    }

    @Test
    @DisplayName("수치가 있는 모호 항목도 모호한 채로 조회된다")
    void keepsAmbiguousWithNumber() {
        // TMAP 에 수록됐는데 0건이면 표기가 다른 것이라 모호로 둔다. 수치는 남아 있다.
        entryRepository.save(entry("1", MentionStatus.AMBIGUOUS, 0L));

        List<OnlineMentionEntry> found = entryRepository.findByContentIds(snapshot, List.of("1"));

        assertThat(found).singleElement().satisfies(entry -> {
            assertThat(entry.getStatus()).isEqualTo(MentionStatus.AMBIGUOUS);
            assertThat(entry.getMentionTotal()).isZero();
            // 수치가 있어도 정렬 대상은 아니다. 0 을 언급량으로 쓰면 유명한 곳이 최하위가 된다.
            assertThat(entry.isSortable()).isFalse();
        });
    }

    @Test
    @DisplayName("묻지 않은 장소와 다른 스냅샷의 항목은 담기지 않는다")
    void keepsToTheAskedSnapshotAndPlaces() {
        OnlineMentionSnapshot previous =
                snapshotRepository.save(snapshot("202608-1", SnapshotStatus.SUPERSEDED));

        entryRepository.saveAll(List.of(
                entry("1", MentionStatus.COLLECTED, 10L),
                entry("2", MentionStatus.COLLECTED, 20L),
                entry(previous, "1", MentionStatus.COLLECTED, 99L)));

        List<OnlineMentionEntry> found = entryRepository.findByContentIds(snapshot, List.of("1"));

        assertThat(found).singleElement().satisfies(entry -> {
            assertThat(entry.getContentId()).isEqualTo("1");
            assertThat(entry.getMentionTotal()).isEqualTo(10L);
        });
    }

    private OnlineMentionEntry entry(String contentId, MentionStatus status, Long total) {
        return entry(snapshot, contentId, status, total);
    }

    private static OnlineMentionEntry entry(OnlineMentionSnapshot snapshot, String contentId,
                                            MentionStatus status, Long total) {
        return OnlineMentionEntry.builder()
                .snapshot(snapshot)
                .contentId(contentId)
                .placeName("경포해변")
                .searchQuery("경포해변 강릉시")
                .mentionTotal(total)
                .status(status)
                .collectedAt(COLLECTED_AT)
                .build();
    }

    private static OnlineMentionSnapshot snapshot(String version, SnapshotStatus status) {
        return OnlineMentionSnapshot.builder()
                .version(version)
                .collectionMonth(version.substring(0, 6))
                .queryRuleVersion("name+sigungu")
                .startedAt(COLLECTED_AT)
                .status(status)
                .targetCount(4)
                .build();
    }
}
