package com.mamoki.tour.infra.tarrltetar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mamoki.tour.domain.relatedplace.dto.RelatedPlaceRow;
import com.mamoki.tour.domain.relatedplace.enums.RelatedPlaceKind;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.tarrltetar.dto.TarRlteTarItem;
import com.mamoki.tour.infra.tarrltetar.dto.TarRlteTarResponse;

/**
 * 저장한 실제 응답으로 TarRlteTarService1 계약을 검증한다.
 * 외부 API 를 호출하지 않으므로 매 테스트마다 안전하게 돌릴 수 있다.
 *
 * <p>fixture 는 강릉시(51150) 를 baseYm=202607 로 실제 호출해 받은 응답에서 기준 관광지
 * 세 곳(경포해변·강릉향교·동부시장)의 행만 남긴 것이다. 관광지·음식·숙박 세 대분류와
 * 다른 시·군 연관이 모두 들어 있다.
 */
class TarRlteTarContractTest {

    private TarRlteTarResponse response;
    private TarRlteTarClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new TarRlteTarClient(properties());

        String body;
        try (InputStream in = getClass().getResourceAsStream(
                "/fixtures/tarrltetar-areaBasedList.json")) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        response = client.parse(body);
    }

    private TarRlteTarProperties properties() {
        return new TarRlteTarProperties(
                "http://example.invalid", "key", "tour", "202607", 1000, 5,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("정상 응답의 헤더와 총 건수를 읽는다")
    void readsHeaderAndTotalCount() {
        assertThat(response.response().header().isSuccess()).isTrue();
        assertThat(response.totalCount()).isEqualTo(63);
        assertThat(response.items()).hasSize(63);
    }

    @Test
    @DisplayName("한 행은 기준 관광지 1곳과 연관 장소 1곳의 짝이다")
    void oneRowIsOnePair() {
        TarRlteTarItem first = response.items().get(0);

        assertThat(first.tAtsNm()).isNotBlank();
        assertThat(first.rlteTatsNm()).isNotBlank();
        assertThat(first.tAtsNm()).isNotEqualTo(first.rlteTatsNm());
    }

    @Test
    @DisplayName("대분류는 관광지·음식·숙박 세 값이며 각각 다른 유형으로 옮겨진다")
    void classifiesThreeCategories() {
        List<RelatedPlaceRow> rows = TarRlteTarItemConverter.convertAll(response.items());

        assertThat(rows).extracting(RelatedPlaceRow::categoryLarge)
                .containsOnly("관광지", "음식", "숙박");
        assertThat(rows).extracting(RelatedPlaceRow::kind)
                .containsOnly(RelatedPlaceKind.ATTRACTION,
                        RelatedPlaceKind.RESTAURANT, RelatedPlaceKind.LODGING);
    }

    @Test
    @DisplayName("기준 관광지명으로 그 장소의 연관 목록만 갈라낼 수 있다")
    void separatesRowsByBaseAttraction() {
        List<RelatedPlaceRow> rows = TarRlteTarItemConverter.convertAll(response.items());

        assertThat(rows).extracting(RelatedPlaceRow::baseName)
                .containsOnly("경포해변", "강릉향교", "동부시장");
        assertThat(rowsOf(rows, "경포해변")).hasSize(50);
        assertThat(rowsOf(rows, "강릉향교")).hasSize(7);
    }

    @Test
    @DisplayName("연관 장소가 다른 시·군일 수 있다")
    void relatedPlaceMayBeInAnotherRegion() {
        List<RelatedPlaceRow> rows = TarRlteTarItemConverter.convertAll(response.items());

        // 기준 관광지는 모두 강릉시(51150) 인데 연관 장소는 다른 시·군에도 있다.
        assertThat(rows).extracting(RelatedPlaceRow::lawdCode)
                .contains("51150", "51210", "51830");
    }

    @Test
    @DisplayName("공급자가 표준 관광지 식별자를 주지 않아 정규화한 이름이 매칭 키다")
    void matchesByNormalizedName() {
        List<RelatedPlaceRow> rows = TarRlteTarItemConverter.convertAll(response.items());
        RelatedPlaceRow row = rowsOf(rows, "경포해변").get(0);

        assertThat(row.baseNormalizedName()).isEqualTo("경포해변");
        assertThat(row.normalizedName()).isNotNull();
    }

    @Test
    @DisplayName("연관 순위를 원본 그대로 읽는다")
    void readsRank() {
        List<RelatedPlaceRow> rows = TarRlteTarItemConverter.convertAll(response.items());

        assertThat(rowsOf(rows, "경포해변").get(0).rank()).isEqualTo(1);
        assertThat(rows).extracting(RelatedPlaceRow::rank).doesNotContainNull();
    }

    @Test
    @DisplayName("기준 관광지명이나 연관 장소명이 없는 행은 어느 짝인지 알 수 없어 버린다")
    void skipsRowsWithoutNames() {
        TarRlteTarItem noBase = item(null, "연관장소", "관광지", "1", "51150");
        TarRlteTarItem noRelated = item("기준장소", "", "관광지", "1", "51150");

        assertThat(TarRlteTarItemConverter.convert(noBase)).isNull();
        assertThat(TarRlteTarItemConverter.convert(noRelated)).isNull();
        assertThat(TarRlteTarItemConverter.convertAll(List.of(noBase, noRelated))).isEmpty();
    }

    @Test
    @DisplayName("순위가 숫자가 아니면 0 이 아니라 null 로 남긴다")
    void keepsBrokenRankNull() {
        RelatedPlaceRow row = TarRlteTarItemConverter.convert(
                item("기준장소", "연관장소", "관광지", "순위아님", "51150"));

        assertThat(row.rank()).isNull();
    }

    @Test
    @DisplayName("시·군 코드 형식이 어긋나면 호출 키를 만들 수 없어 null 로 남긴다")
    void keepsBrokenLawdCodeNull() {
        RelatedPlaceRow row = TarRlteTarItemConverter.convert(
                item("기준장소", "연관장소", "관광지", "1", "51"));

        assertThat(row.lawdCode()).isNull();
    }

    @Test
    @DisplayName("모르는 대분류는 관광지로 넘겨짚지 않는다")
    void doesNotGuessUnknownCategory() {
        RelatedPlaceRow row = TarRlteTarItemConverter.convert(
                item("기준장소", "연관장소", "레저", "1", "51150"));

        assertThat(row.kind()).isEqualTo(RelatedPlaceKind.OTHER);
        assertThat(row.categoryLarge()).isEqualTo("레저");
    }

    @Test
    @DisplayName("공급자 오류 응답은 외부 호출 실패로 변환한다")
    void convertsProviderErrorToExternalApiException() {
        String body = """
                {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"}}}""";

        assertThatThrownBy(() -> client.parse(body))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("TarRlteTarService1");
    }

    @Test
    @DisplayName("JSON 이 아닌 게이트웨이 응답도 외부 호출 실패로 변환한다")
    void convertsXmlGatewayErrorToExternalApiException() {
        String body = "<OpenAPI_ServiceResponse><cmmMsgHeader>...</cmmMsgHeader></OpenAPI_ServiceResponse>";

        assertThatThrownBy(() -> client.parse(body))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("캐시 키는 같은 조건이면 항상 같고 인증키를 담지 않는다")
    void cacheKeyIsStableAndHasNoServiceKey() {
        String key = client.relatedListKey("51150", 1);

        assertThat(key).isEqualTo(client.relatedListKey("51150", 1));
        assertThat(key).doesNotContain("key");
        assertThat(key).contains("signguCd=51150", "areaCd=51", "baseYm=202607", "pageNo=1");
        assertThat(key).isNotEqualTo(client.relatedListKey("51150", 2));
    }

    @Test
    @DisplayName("기준 월을 지정하지 않으면 공급자 제공 지연을 감안해 두 달 전을 쓴다")
    void fallsBackToTwoMonthsAgo() {
        TarRlteTarProperties noBaseYm = new TarRlteTarProperties(
                "http://example.invalid", "key", "tour", null, 1000, 5,
                Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThat(noBaseYm.resolveBaseYm(LocalDate.of(2026, 9, 8))).isEqualTo("202607");
    }

    private static List<RelatedPlaceRow> rowsOf(List<RelatedPlaceRow> rows, String baseName) {
        return rows.stream().filter(row -> baseName.equals(row.baseName())).toList();
    }

    private static TarRlteTarItem item(String baseName, String relatedName,
                                       String categoryLarge, String rank, String lawdCode) {

        return new TarRlteTarItem("202607", "code", baseName, "51", "강원특별자치도",
                "51150", "강릉시", "relatedCode", relatedName, "51", "강원특별자치도",
                lawdCode, "강릉시", categoryLarge, "중분류", "소분류", rank);
    }
}
