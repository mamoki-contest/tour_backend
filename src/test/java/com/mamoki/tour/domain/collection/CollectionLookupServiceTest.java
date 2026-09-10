package com.mamoki.tour.domain.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.attraction.dto.AttractionDetailSnapshot;
import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.attraction.dto.OnlineMentionView;
import com.mamoki.tour.domain.attraction.dto.TmapRankView;
import com.mamoki.tour.domain.attraction.service.AttractionDetailService;
import com.mamoki.tour.domain.attraction.service.AttractionDetailService.BasicLookup;
import com.mamoki.tour.domain.attraction.service.AttractionService;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.collection.dto.CollectionLookupResponse;
import com.mamoki.tour.domain.collection.enums.CollectionItemStatus;
import com.mamoki.tour.domain.collection.service.CollectionLookupService;
import com.mamoki.tour.global.exception.ServiceException;

/**
 * 저장된 식별자 일괄 재조회.
 *
 * <p>"없어진 항목"과 "이번에 확인하지 못한 항목"이 섞이지 않는지를 특히 확인한다.
 * 둘을 같게 다루면 사용자가 저장해 둔 장소를 지우게 된다.
 */
class CollectionLookupServiceTest {

    private static final String FOUND = "126508";
    private static final String GONE = "999999";
    private static final String UNREACHABLE = "888888";

    private AttractionDetailService attractionDetailService;
    private CollectionLookupService service;

    @BeforeEach
    void setUp() {
        attractionDetailService = Mockito.mock(AttractionDetailService.class);

        given(attractionDetailService.findBasic(anyString())).willAnswer(invocation -> {
            String contentId = invocation.getArgument(0);

            // 공급자를 부르지 못한 경우. 본문이 없다.
            if (UNREACHABLE.equals(contentId)) {
                return new BasicLookup(null, CachedResponse.noData());
            }

            // 응답은 받았지만 그 식별자의 항목이 없는 경우.
            if (GONE.equals(contentId)) {
                return new BasicLookup(null, CachedResponse.available("{}", LocalDateTime.now()));
            }

            return new BasicLookup(detailOf(contentId),
                    CachedResponse.available("{}", LocalDateTime.now()));
        });

        AttractionService attractionService = Mockito.mock(AttractionService.class);
        given(attractionService.describe(any(), any(), any(), any())).willAnswer(invocation -> {
            List<AttractionSnapshot> snapshots = invocation.getArgument(0);

            return snapshots.stream()
                    .map(snapshot -> AttractionResponse.of(snapshot, "강릉시", null,
                            OnlineMentionView.notCollected(null), TmapRankView.notAvailable(), null))
                    .toList();
        });

        service = new CollectionLookupService(attractionDetailService, attractionService);
    }

    private static AttractionDetailSnapshot detailOf(String contentId) {
        AttractionSnapshot basic = new AttractionSnapshot(contentId, "경포해변", null,
                "강원특별자치도 강릉시", new BigDecimal("37.8"), new BigDecimal("128.9"),
                "12", "51150", LocalDateTime.now(), "KorService2");

        return new AttractionDetailSnapshot(basic, null, null, null, null);
    }

    @Test
    @DisplayName("저장된 식별자로 최신 표시정보를 다시 조회한다")
    void returnsLatestDisplayInfo() {
        CollectionLookupResponse response = service.lookup(List.of(FOUND));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).status()).isEqualTo(CollectionItemStatus.AVAILABLE);
        assertThat(response.items().get(0).attraction().name()).isEqualTo("경포해변");
        assertThat(response.items().get(0).attraction().regionName()).isEqualTo("강릉시");
        assertThat(response.availableCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("없어진 식별자는 NOT_FOUND 로 표시되고 나머지는 정상으로 돌아온다")
    void marksGoneIdWithoutFailingWholeRequest() {
        CollectionLookupResponse response = service.lookup(List.of(FOUND, GONE));

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).status()).isEqualTo(CollectionItemStatus.AVAILABLE);
        assertThat(response.items().get(1).status()).isEqualTo(CollectionItemStatus.NOT_FOUND);
        assertThat(response.items().get(1).attraction()).isNull();
        assertThat(response.notFoundCount()).isEqualTo(1);
        assertThat(response.availableCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("공급자를 부르지 못한 항목은 없어진 항목이 아니라 UNAVAILABLE 이다")
    void separatesUnreachableFromGone() {
        CollectionLookupResponse response = service.lookup(List.of(UNREACHABLE, GONE));

        assertThat(response.items().get(0).status()).isEqualTo(CollectionItemStatus.UNAVAILABLE);
        assertThat(response.items().get(1).status()).isEqualTo(CollectionItemStatus.NOT_FOUND);
        assertThat(response.unavailableCount()).isEqualTo(1);
        assertThat(response.notFoundCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("요청한 순서와 개수를 그대로 유지한다")
    void keepsRequestedOrder() {
        CollectionLookupResponse response = service.lookup(List.of(GONE, FOUND, UNREACHABLE));

        assertThat(response.items()).extracting(item -> item.contentId())
                .containsExactly(GONE, FOUND, UNREACHABLE);
        assertThat(response.requestedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 식별자를 두 번 저장했어도 공급자를 한 번만 부른다")
    void deduplicatesContentIds() {
        CollectionLookupResponse response = service.lookup(List.of(FOUND, FOUND));

        assertThat(response.items()).hasSize(1);
        assertThat(response.requestedCount()).isEqualTo(1);
        Mockito.verify(attractionDetailService, Mockito.times(1)).findBasic(FOUND);
    }

    @Test
    @DisplayName("식별자가 없으면 거절한다")
    void rejectsEmptyRequest() {
        assertThatThrownBy(() -> service.lookup(List.of()))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.lookup(List.of("  ")))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("한 번에 조회할 수 있는 수를 넘으면 거절한다")
    void rejectsTooManyContentIds() {
        List<String> tooMany = IntStream.rangeClosed(0, CollectionLookupService.MAX_CONTENT_IDS)
                .mapToObj(index -> "id" + index)
                .toList();

        assertThatThrownBy(() -> service.lookup(tooMany))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("사용자별 저장소를 두지 않고 요청받은 식별자만 다룬다")
    void keepsNoUserState() {
        service.lookup(List.of(FOUND));
        CollectionLookupResponse second = service.lookup(List.of(GONE));

        // 앞선 조회가 다음 조회에 남지 않는다.
        assertThat(second.items()).hasSize(1);
        assertThat(second.items().get(0).contentId()).isEqualTo(GONE);
    }
}
