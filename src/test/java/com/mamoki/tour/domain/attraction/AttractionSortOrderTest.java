package com.mamoki.tour.domain.attraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSort;
import com.mamoki.tour.domain.attraction.dto.OnlineMentionView;
import com.mamoki.tour.domain.attraction.dto.TmapRankView;
import com.mamoki.tour.domain.attraction.dto.VisitorStatsView;
import com.mamoki.tour.domain.attraction.support.AttractionSortOrder;
import com.mamoki.tour.global.enums.MentionStatus;

/**
 * 정렬 규칙만 따로 검증한다.
 *
 * <p>정렬은 공급자 호출과 무관한 순수 계산이라 외부 의존 없이 확인할 수 있다.
 * 여기서 쓰는 비교 규칙은 {@code AttractionService} 가 쓰는 것과 같은 규칙이다.
 */
class AttractionSortOrderTest {

    private AttractionResponse place(String name, Long mentionCount) {
        OnlineMentionView mention = mentionCount == null
                ? OnlineMentionView.notCollected("name+sigungu")
                : new OnlineMentionView(MentionStatus.COLLECTED, mentionCount,
                        LocalDateTime.of(2026, 9, 6, 0, 0), "name+sigungu");

        return new AttractionResponse(name, name, null, null, null, null, "12", "51150",
                "강릉시", null, LocalDateTime.now(), mention,
                TmapRankView.notAvailable(), VisitorStatsView.notImported());
    }

    private AttractionResponse ambiguous(String name) {
        return new AttractionResponse(name, name, null, null, null, null, "12", "51150",
                "강릉시", null, LocalDateTime.now(),
                new OnlineMentionView(MentionStatus.AMBIGUOUS, null, null, "name+sigungu"),
                TmapRankView.notAvailable(), VisitorStatsView.notImported());
    }

    private List<String> namesOf(List<AttractionResponse> items) {
        return items.stream().map(AttractionResponse::name).toList();
    }

    private List<AttractionResponse> sorted(List<AttractionResponse> items, AttractionSort sort) {
        return new AttractionSortOrder().order(items, sort);
    }

    @Test
    @DisplayName("언급 많은 순으로 정렬한다")
    void sortsDescending() {
        List<AttractionResponse> items = List.of(
                place("경포해변", 140006L), place("속초해변", 170653L), place("오봉저수지", 84L));

        assertThat(namesOf(sorted(items, AttractionSort.ONLINE_MENTION_DESC)))
                .containsExactly("속초해변", "경포해변", "오봉저수지");
    }

    @Test
    @DisplayName("언급 적은 순은 같은 집합을 정확히 반대로 정렬한다")
    void sortsAscendingAsExactReverse() {
        List<AttractionResponse> items = List.of(
                place("경포해변", 140006L), place("속초해변", 170653L), place("오봉저수지", 84L));

        List<String> desc = namesOf(sorted(items, AttractionSort.ONLINE_MENTION_DESC));
        List<String> asc = namesOf(sorted(items, AttractionSort.ONLINE_MENTION_ASC));

        assertThat(asc).containsExactly("오봉저수지", "경포해변", "속초해변");
        assertThat(asc).isEqualTo(desc.reversed());
    }

    @Test
    @DisplayName("동률은 관광지명 오름차순으로 고정한다")
    void breaksTiesByName() {
        List<AttractionResponse> items = List.of(
                place("다해변", 100L), place("가해변", 100L), place("나해변", 100L));

        assertThat(namesOf(sorted(items, AttractionSort.ONLINE_MENTION_DESC)))
                .containsExactly("가해변", "나해변", "다해변");
        assertThat(namesOf(sorted(items, AttractionSort.ONLINE_MENTION_ASC)))
                .containsExactly("가해변", "나해변", "다해변");
    }

    @Test
    @DisplayName("정상 0건은 산정된 값으로 다뤄 가장 적은 쪽에 놓는다")
    void treatsZeroAsCollected() {
        List<AttractionResponse> items = List.of(place("가해변", 100L), place("나해변", 0L));

        assertThat(namesOf(sorted(items, AttractionSort.ONLINE_MENTION_ASC)))
                .containsExactly("나해변", "가해변");
    }

    @Test
    @DisplayName("미산정 장소는 언급 적은 순 상단에 오지 않는다")
    void keepsUncollectedOutOfAscendingTop() {
        List<AttractionResponse> items = List.of(
                place("미산정곳", null), place("가해변", 100L), place("나해변", 5L));

        assertThat(namesOf(sorted(items, AttractionSort.ONLINE_MENTION_ASC)))
                .containsExactly("나해변", "가해변", "미산정곳");
    }

    @Test
    @DisplayName("모호한 장소도 정보 없음 구역으로 보낸다")
    void keepsAmbiguousOutOfSortedRegion() {
        List<AttractionResponse> items = List.of(
                ambiguous("해수욕장"), place("가해변", 100L));

        assertThat(namesOf(sorted(items, AttractionSort.ONLINE_MENTION_DESC)))
                .containsExactly("가해변", "해수욕장");
        assertThat(namesOf(sorted(items, AttractionSort.ONLINE_MENTION_ASC)))
                .containsExactly("가해변", "해수욕장");
    }

    @Test
    @DisplayName("정보 없음 구역도 관광지명 오름차순으로 고정한다")
    void ordersUnsortableByName() {
        List<AttractionResponse> items = List.of(
                place("다미산정", null), place("가미산정", null), place("나미산정", null));

        assertThat(namesOf(sorted(items, AttractionSort.ONLINE_MENTION_DESC)))
                .containsExactly("가미산정", "나미산정", "다미산정");
    }

    @Test
    @DisplayName("두 정렬 방향의 산정 장소 집합은 서로 같다")
    void sameSortableSetInBothDirections() {
        List<AttractionResponse> items = List.of(
                place("가해변", 100L), place("미산정곳", null), place("나해변", 5L), ambiguous("해수욕장"));

        List<String> desc = namesOf(sorted(items, AttractionSort.ONLINE_MENTION_DESC)).subList(0, 2);
        List<String> asc = namesOf(sorted(items, AttractionSort.ONLINE_MENTION_ASC)).subList(0, 2);

        assertThat(desc).containsExactlyInAnyOrderElementsOf(asc);
    }
}
