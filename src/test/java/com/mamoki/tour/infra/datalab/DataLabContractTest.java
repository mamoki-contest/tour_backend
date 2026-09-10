package com.mamoki.tour.infra.datalab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.region.dto.RegionVisitors;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.datalab.dto.DataLabResponse;

/**
 * 저장한 실제 응답으로 DataLabService 계약을 검증한다.
 * 외부 API 를 호출하지 않으므로 매 테스트마다 안전하게 돌릴 수 있다.
 *
 * <p>fixture 는 2026-08-10 하루를 실제 호출해 받은 전국 807행에서 강원 18개 시·군(54행)과
 * 강원 밖 2개 시·군구(6행)만 남긴 것이다. 강원만 추리는 동작을 확인하려고 밖의 행을 남겼다.
 */
class DataLabContractTest {

    private DataLabResponse response;

    @BeforeEach
    void setUp() throws Exception {
        DataLabClient client = new DataLabClient(properties());

        String body;
        try (InputStream in = getClass().getResourceAsStream(
                "/fixtures/datalab-locgoRegnVisitrDDList.json")) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        response = client.parse(body);
    }

    private DataLabProperties properties() {
        return new DataLabProperties("http://example.invalid", "key", "tour", null,
                30, 7, 1000, 10, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("정상 응답의 헤더와 총 건수를 읽는다")
    void readsHeaderAndTotalCount() {
        assertThat(response.response().header().isSuccess()).isTrue();
        assertThat(response.totalCount()).isEqualTo(807);
        assertThat(response.items()).hasSize(60);
    }

    @Test
    @DisplayName("한 시·군구가 관광객 구분 세 행으로 오며 시·군 단위 합계로 묶인다")
    void groupsRowsByRegion() {
        List<RegionVisitors> aggregated = DataLabItemConverter.convertAll(response.items());

        // 강원 18개 + 강원 밖 2개
        assertThat(aggregated).hasSize(20);
        assertThat(aggregated).extracting(RegionVisitors::dayCount).containsOnly(1);
    }

    @Test
    @DisplayName("현지인은 방문 규모에서 뺀다. 거주 인구를 관광 방문으로 세지 않는다")
    void excludesLocalVisitors() {
        Map<String, RegionVisitors> byLawdCode = byLawdCode();

        // 춘천시(51110) 원본: 현지인 237863.5, 외지인 70273.0, 외국인 1612.54
        assertThat(byLawdCode.get("51110").visitorCount()).isEqualTo(71886L);
    }

    @Test
    @DisplayName("소수점 추정치를 반올림해 사람 수로 담는다")
    void roundsEstimatedVisitors() {
        assertThat(byLawdCode().get("51110").visitorCount()).isNotNull();
        assertThat(byLawdCode().values()).allSatisfy(
                visitors -> assertThat(visitors.visitorCount()).isPositive());
    }

    @Test
    @DisplayName("강원 18개 시·군이 모두 담긴다")
    void coversAllGangwonRegions() {
        List<String> gangwon = byLawdCode().keySet().stream()
                .filter(lawdCode -> lawdCode.startsWith("51"))
                .sorted()
                .toList();

        assertThat(gangwon).hasSize(18);
        assertThat(gangwon).startsWith("51110", "51130", "51150");
    }

    @Test
    @DisplayName("오류 응답은 예외로 바꾼다. 빈 목록으로 위장하지 않는다")
    void rejectsErrorResponse() {
        DataLabClient client = new DataLabClient(properties());

        assertThatThrownBy(() -> client.parse(
                "{\"response\":{\"header\":{\"resultCode\":\"10\",\"resultMsg\":\"INVALID\"}}}"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("기준 기간은 공개 지연을 뺀 날에서 창 길이만큼 거슬러 잡는다")
    void resolvesPeriodFromLag() {
        DataLabProperties properties = properties();
        LocalDate today = LocalDate.of(2026, 9, 10);

        assertThat(properties.resolveEndDate(today)).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(properties.resolveStartDate(today)).isEqualTo(LocalDate.of(2026, 8, 5));
    }

    @Test
    @DisplayName("기준일을 고정하면 지연 계산 대신 그 값을 쓴다")
    void honoursFixedEndDate() {
        DataLabProperties fixed = new DataLabProperties("http://example.invalid", "key", "tour",
                "20260731", 30, 7, 1000, 10, Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThat(fixed.resolveEndDate(LocalDate.of(2026, 9, 10)))
                .isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(fixed.resolveStartDate(LocalDate.of(2026, 9, 10)))
                .isEqualTo(LocalDate.of(2026, 7, 25));
    }

    private Map<String, RegionVisitors> byLawdCode() {
        return DataLabItemConverter.convertAll(response.items()).stream()
                .collect(Collectors.toMap(RegionVisitors::lawdCode, Function.identity()));
    }
}
