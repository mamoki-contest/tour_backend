package com.mamoki.tour.infra.korservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.infra.korservice.dto.KorServiceItem;
import com.mamoki.tour.infra.korservice.dto.KorServiceResponse;

/**
 * 저장한 실제 응답으로 KorService2 계약을 검증한다.
 * 외부 API 를 호출하지 않으므로 매 테스트마다 안전하게 돌릴 수 있다.
 */
class KorServiceContractTest {

    private KorServiceResponse response;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/fixtures/korservice-areaBasedList2.json")) {
            response = new ObjectMapper().readValue(in, KorServiceResponse.class);
        }
    }

    @Test
    @DisplayName("정상 응답의 헤더와 총 건수를 읽는다")
    void readsHeaderAndTotalCount() {
        assertThat(response.response().header().isSuccess()).isTrue();
        assertThat(response.totalCount()).isEqualTo(676);
        assertThat(response.items()).hasSize(3);
    }

    @Test
    @DisplayName("원본 항목을 표준 계약으로 변환한다")
    void convertsToStandardContract() {
        AttractionSnapshot snapshot = KorServiceItemConverter.convert(response.items().get(0));

        assertThat(snapshot.contentId()).isEqualTo("2868839");
        assertThat(snapshot.name()).isEqualTo("가람집옹심이");
        assertThat(snapshot.address()).isEqualTo("강원특별자치도 강릉시 공항길30번길 16");
        assertThat(snapshot.latitude()).isEqualByComparingTo(new BigDecimal("37.7611934162"));
        assertThat(snapshot.longitude()).isEqualByComparingTo(new BigDecimal("128.9393320379"));
        assertThat(snapshot.contentTypeId()).isEqualTo("39");
        assertThat(snapshot.source()).isEqualTo("KorService2");
    }

    @Test
    @DisplayName("법정동 시·도 코드와 시·군 코드를 5자리로 결합한다")
    void composesLawdCode() {
        AttractionSnapshot snapshot = KorServiceItemConverter.convert(response.items().get(0));

        assertThat(snapshot.lawdCode()).isEqualTo("51150");
    }

    @Test
    @DisplayName("공급자의 빈 문자열은 빈 값이 아니라 null 로 정규화한다")
    void normalizesBlankToNull() {
        AttractionSnapshot withoutImage = KorServiceItemConverter.convert(response.items().get(1));

        assertThat(withoutImage.imageUrl()).isNull();
    }

    @Test
    @DisplayName("addr2 가 있으면 주소를 합치고 없으면 addr1 만 사용한다")
    void joinsAddressParts() {
        AttractionSnapshot withAddr2 = KorServiceItemConverter.convert(response.items().get(2));

        assertThat(withAddr2.address())
                .isEqualTo("강원특별자치도 강릉시 공항길29번길 7 (병산동) 2층");
    }

    @Test
    @DisplayName("modifiedtime 을 기준 시점으로 해석한다")
    void parsesBaseAt() {
        AttractionSnapshot snapshot = KorServiceItemConverter.convert(response.items().get(0));

        assertThat(snapshot.baseAt()).isEqualTo(LocalDateTime.of(2025, 9, 4, 14, 15, 26));
    }

    @Test
    @DisplayName("좌표나 기준 시점이 깨져 있어도 임의 값을 만들지 않는다")
    void keepsBrokenValuesNull() {
        KorServiceItem broken = new KorServiceItem(
                "1", "12", "값 없는 장소", "", "", "", "",
                "좌표아님", "", "32", "1", "", "", "", "시각아님");

        AttractionSnapshot snapshot = KorServiceItemConverter.convert(broken);

        assertThat(snapshot.latitude()).isNull();
        assertThat(snapshot.longitude()).isNull();
        assertThat(snapshot.baseAt()).isNull();
        assertThat(snapshot.address()).isNull();
        assertThat(snapshot.lawdCode()).isNull();
    }

    @Test
    @DisplayName("표준 식별자나 이름이 없는 항목은 카탈로그에 넣지 않는다")
    void skipsUnusableItems() {
        KorServiceItem noName = new KorServiceItem(
                "1", "12", "", "", "", "", "", "", "", "32", "1", "51", "150", "", "");

        assertThat(KorServiceItemConverter.convert(noName)).isNull();
        assertThat(KorServiceItemConverter.convertAll(List.of(noName))).isEmpty();
    }
}
