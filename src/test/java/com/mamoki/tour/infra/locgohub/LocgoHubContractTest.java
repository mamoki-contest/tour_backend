package com.mamoki.tour.infra.locgohub;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mamoki.tour.domain.attraction.dto.CenterRank;
import com.mamoki.tour.infra.locgohub.dto.LocgoHubItem;
import com.mamoki.tour.infra.locgohub.dto.LocgoHubResponse;

/** 저장한 실제 응답으로 LocgoHubTarService1 계약을 검증한다. */
class LocgoHubContractTest {

    private LocgoHubResponse response;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/fixtures/locgohub-areaBasedList1.json")) {
            response = new ObjectMapper().readValue(in, LocgoHubResponse.class);
        }
    }

    @Test
    @DisplayName("정상 응답의 헤더와 총 건수를 읽는다")
    void readsHeaderAndTotalCount() {
        assertThat(response.response().header().isSuccess()).isTrue();
        assertThat(response.totalCount()).isEqualTo(100);
        assertThat(response.items()).hasSize(5);
    }

    @Test
    @DisplayName("원본 항목을 중심관광지 순위 계약으로 변환한다")
    void convertsToCenterRank() {
        CenterRank first = LocgoHubItemConverter.convert(response.items().get(0));

        assertThat(first.name()).isEqualTo("강릉중앙시장");
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.lawdCode()).isEqualTo("51150");
        assertThat(first.baseYm()).isEqualTo("202507");
        assertThat(first.latitude()).isEqualByComparingTo(new BigDecimal("37.753986783023300"));
        assertThat(first.longitude()).isEqualByComparingTo(new BigDecimal("128.898613909450000"));
    }

    @Test
    @DisplayName("데이터랩 식별자를 보존한다 - 관심도 CSV 와 같은 체계다")
    void keepsDataLabId() {
        List<CenterRank> ranks = LocgoHubItemConverter.convertAll(response.items());

        assertThat(ranks)
                .filteredOn(rank -> "경포해변".equals(rank.name()))
                .singleElement()
                .extracting(CenterRank::dataLabId)
                .isEqualTo("1597d21403f63da1bb0539592597a525");
    }

    @Test
    @DisplayName("표기가 다른 이름도 정규화하면 같아진다")
    void normalizesNameNotation() {
        CenterRank arte = LocgoHubItemConverter.convertAll(response.items()).stream()
                .filter(rank -> rank.name().startsWith("아르떼"))
                .findFirst()
                .orElseThrow();

        assertThat(arte.name()).isEqualTo("아르떼뮤지엄/강릉");
        assertThat(arte.normalizedName()).isEqualTo("아르떼뮤지엄강릉");
    }

    @Test
    @DisplayName("순위나 이름이 없는 항목은 쓰지 않는다")
    void skipsUnusableItems() {
        LocgoHubItem noRank = new LocgoHubItem("202507", "128.9", "37.7", "51", "강원특별자치도",
                "51150", "강릉시", "abc", "이름있음", "관광지", "쇼핑", "");
        LocgoHubItem noName = new LocgoHubItem("202507", "128.9", "37.7", "51", "강원특별자치도",
                "51150", "강릉시", "abc", "", "관광지", "쇼핑", "1");

        assertThat(LocgoHubItemConverter.convert(noRank)).isNull();
        assertThat(LocgoHubItemConverter.convert(noName)).isNull();
        assertThat(LocgoHubItemConverter.convertAll(List.of(noRank, noName))).isEmpty();
    }

    @Test
    @DisplayName("좌표가 깨져 있어도 임의 값을 만들지 않는다")
    void keepsBrokenCoordinateNull() {
        LocgoHubItem broken = new LocgoHubItem("202507", "좌표아님", "", "51", "강원특별자치도",
                "51150", "강릉시", "abc", "장소", "관광지", "쇼핑", "7");

        CenterRank rank = LocgoHubItemConverter.convert(broken);

        assertThat(rank.latitude()).isNull();
        assertThat(rank.longitude()).isNull();
        assertThat(rank.rank()).isEqualTo(7);
    }
}
