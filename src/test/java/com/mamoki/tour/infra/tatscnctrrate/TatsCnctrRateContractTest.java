package com.mamoki.tour.infra.tatscnctrrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.math.BigDecimal;
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

import com.mamoki.tour.domain.visittiming.dto.AttractionForecast;
import com.mamoki.tour.domain.visittiming.dto.DailyConcentration;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.tatscnctrrate.dto.TatsCnctrRateItem;
import com.mamoki.tour.infra.tatscnctrrate.dto.TatsCnctrRateResponse;

/**
 * 저장한 실제 응답으로 TatsCnctrRateService 계약을 검증한다.
 * 외부 API 를 호출하지 않으므로 매 테스트마다 안전하게 돌릴 수 있다.
 *
 * <p>fixture 는 강릉시(51150) 를 numOfRows=100 으로 실제 호출해 받은 응답이다.
 * 3곳은 30일치가 모두 들어 있고, 4번째 장소는 페이지 경계에서 10일치만 잘려 들어 있다.
 */
class TatsCnctrRateContractTest {

    private TatsCnctrRateResponse response;
    private TatsCnctrRateClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new TatsCnctrRateClient(properties());

        String body;
        try (InputStream in = getClass().getResourceAsStream(
                "/fixtures/tatscnctrrate-tatsCnctrRatedList.json")) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        response = client.parse(body);
    }

    private TatsCnctrRateProperties properties() {
        return new TatsCnctrRateProperties(
                "http://example.invalid", "key", "tour", 100, 5,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("정상 응답의 헤더와 총 건수를 읽는다")
    void readsHeaderAndTotalCount() {
        assertThat(response.response().header().isSuccess()).isTrue();
        assertThat(response.totalCount()).isEqualTo(2880);
        assertThat(response.items()).hasSize(100);
    }

    @Test
    @DisplayName("한 행은 관광지 1곳의 하루치이며 장소 단위로 다시 묶인다")
    void groupsRowsByPlace() {
        List<AttractionForecast> forecasts =
                TatsCnctrRateItemConverter.convertAll(response.items());

        assertThat(forecasts).hasSize(4);
        assertThat(forecasts).extracting(AttractionForecast::name)
                .containsExactly("강남축구공원", "강릉 경포대", "강릉 경포해수욕장", "강릉 굴산사지");
    }

    @Test
    @DisplayName("공급자가 내려준 만큼만 담는다. 페이지 경계에서 잘린 장소를 30일로 채우지 않는다")
    void keepsOnlyProvidedDays() {
        Map<String, AttractionForecast> byName = byName();

        assertThat(byName.get("강릉 경포대").days()).hasSize(30);
        // 페이지가 100행에서 끊겨 10일치만 들어온 장소. 나머지 20일을 지어내지 않는다.
        assertThat(byName.get("강릉 굴산사지").days()).hasSize(10);
    }

    @Test
    @DisplayName("예측 창은 조회일 기준 30일이며 날짜 오름차순으로 정렬된다")
    void sortsDaysAscending() {
        List<DailyConcentration> days = byName().get("강릉 경포대").days();

        assertThat(days.get(0).date()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(days.get(days.size() - 1).date()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(days).extracting(DailyConcentration::date).isSorted();
    }

    @Test
    @DisplayName("집중률은 원본 소수 그대로 보존한다")
    void keepsRateAsProvided() {
        List<DailyConcentration> days = byName().get("강릉 경포대").days();

        assertThat(days.get(0).rate()).isEqualByComparingTo(new BigDecimal("46.4"));
        assertThat(days.get(0).hasRate()).isTrue();
    }

    @Test
    @DisplayName("공급자가 좌표를 주지 않으므로 좌표는 null 이다")
    void hasNoCoordinates() {
        // 실제 응답에 mapX·mapY 가 없다. 없는 값을 0 이나 다른 장소의 좌표로 채우지 않는다.
        AttractionForecast forecast = byName().get("강릉 경포대");

        assertThat(forecast.latitude()).isNull();
        assertThat(forecast.longitude()).isNull();
        assertThat(forecast.lawdCode()).isEqualTo("51150");
    }

    @Test
    @DisplayName("표기가 달라도 정규화한 이름으로 같은 장소를 찾을 수 있다")
    void exposesNormalizedName() {
        AttractionForecast forecast = byName().get("강릉 경포대");

        assertThat(forecast.normalizedName()).isEqualTo("강릉경포대");
    }

    @Test
    @DisplayName("집중률이 비어 있거나 숫자가 아니면 0 이 아니라 null 로 남긴다")
    void keepsBrokenRateNull() {
        List<AttractionForecast> forecasts = TatsCnctrRateItemConverter.convertAll(List.of(
                item("20260907", "값없는장소", ""),
                item("20260908", "값없는장소", "숫자아님"),
                item("20260909", "값없는장소", "0")));

        List<DailyConcentration> days = forecasts.get(0).days();

        assertThat(days).hasSize(3);
        assertThat(days.get(0).rate()).isNull();
        assertThat(days.get(1).rate()).isNull();
        // 정상값 0 과 수집 실패를 구분한다.
        assertThat(days.get(2).rate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(days.get(2).hasRate()).isTrue();
    }

    @Test
    @DisplayName("이름이나 날짜가 없는 행은 어느 장소의 어느 날인지 알 수 없어 버린다")
    void skipsUnusableRows() {
        List<AttractionForecast> forecasts = TatsCnctrRateItemConverter.convertAll(List.of(
                item("20260907", "", "10"),
                item("", "이름있는장소", "10"),
                item("날짜아님", "이름있는장소", "10")));

        assertThat(forecasts).isEmpty();
    }

    @Test
    @DisplayName("같은 날짜가 두 번 오면 먼저 온 값을 남긴다")
    void keepsFirstValueOnDuplicateDate() {
        List<AttractionForecast> forecasts = TatsCnctrRateItemConverter.convertAll(List.of(
                item("20260907", "중복장소", "10"),
                item("20260907", "중복장소", "99")));

        assertThat(forecasts.get(0).days()).hasSize(1);
        assertThat(forecasts.get(0).days().get(0).rate()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    @DisplayName("공급자 오류 응답은 외부 호출 실패로 변환한다")
    void convertsProviderErrorToException() {
        String error = """
                {"response":{"header":{"resultCode":"30","resultMsg":"SERVICE KEY IS NOT REGISTERED ERROR"}}}""";

        assertThatThrownBy(() -> client.parse(error))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("오류를 반환했습니다");
    }

    @Test
    @DisplayName("JSON 이 아닌 게이트웨이 응답도 외부 호출 실패로 변환한다")
    void convertsNonJsonToException() {
        assertThatThrownBy(() -> client.parse("<OpenAPI_ServiceResponse><cmmMsgHeader/></OpenAPI_ServiceResponse>"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("해석하지 못했습니다");
    }

    @Test
    @DisplayName("캐시 키는 같은 조건이면 항상 같고 인증키를 담지 않는다")
    void buildsStableCacheKey() {
        String key = client.tatsCnctrRatedListKey("51150", 1);

        assertThat(key).isEqualTo(client.tatsCnctrRatedListKey("51150", 1));
        assertThat(key).contains("signguCd=51150").contains("pageNo=1");
        assertThat(key).doesNotContain("serviceKey");
    }

    private Map<String, AttractionForecast> byName() {
        return TatsCnctrRateItemConverter.convertAll(response.items()).stream()
                .collect(Collectors.toMap(AttractionForecast::name, Function.identity()));
    }

    private static TatsCnctrRateItem item(String baseYmd, String name, String rate) {
        return new TatsCnctrRateItem(
                baseYmd, "51", "강원특별자치도", "51150", "강릉시", name, rate, null, null);
    }
}
