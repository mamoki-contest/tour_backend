package com.mamoki.tour.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClient;

import com.mamoki.tour.domain.cache.repository.ExternalApiCacheRepository;

/**
 * 컨트롤러에서 DB 까지 지나되, 공급자 자리는 {@link MockRestServiceServer} 로 막은 관통 테스트.
 *
 * <p>{@link AttractionListThroughTest} 와 지나는 길은 같다. 다른 것은 공급자를 무엇으로
 * 대신하는가다. 그쪽은 JDK {@code HttpServer} 로 실제 포트를 열고, 이쪽은 아무 포트도 열지
 * 않는다. 클라이언트가 {@code RestClient.Builder} 를 밖에서 받게 되면서(#66) 빈 하나만 갈아
 * 끼우면 되기 때문이다.
 *
 * <p>얻은 것은 <b>나가는 요청을 선언적으로 검사할 수 있다</b>는 점이다. 아래 첫 테스트는
 * 공공데이터포털 Encoding 키가 전체 스택을 지나는 동안 한 번도 다시 인코딩되지 않는 것을
 * 요청 URI 에서 직접 본다. 실제 HTTP 서버로는 이것을 보려면 핸들러 안에서 문자열을 손으로
 * 헤집어야 했다.
 *
 * <p>네 갈래(성공·캐시 적중·STALE·NO_DATA)는 {@code AttractionListThroughTest} 가 이미
 * 덮고 있다. 여기서는 겹치지 않게 <b>요청의 모양</b>과 공급자 장애 한 갈래만 본다.
 *
 * <p>{@link TestBean} 이 컨텍스트 캐시 키를 바꾸므로 이 테스트도 컨텍스트를 하나 더 만든다.
 * 그래서 스키마도 전용으로 쓴다({@link Schema}) — 자세한 이유는
 * {@link ThroughTestSchemaInitializer} 에 적었다(#86).
 */
@SpringBootTest(properties = {
        "tour.api.kor-service.base-url=" + AttractionListMockServerThroughTest.BASE_URL,
        "tour.api.kor-service.service-key=" + AttractionListMockServerThroughTest.ENCODING_KEY
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = AttractionListMockServerThroughTest.Schema.class)
class AttractionListMockServerThroughTest {

    /** 다른 관통 테스트와도 스키마를 나눈다. 병렬 실행에서는 둘이 동시에 뜰 수 있다. */
    static class Schema extends ThroughTestSchemaInitializer {
        Schema() {
            super("_through_mock");
        }
    }

    /** {@code KOR_SERVICE_BASE_URL} 오버라이드가 스택을 지나서도 지켜지는지 함께 본다. */
    static final String BASE_URL = "https://stub.invalid/B551011/KorService2";

    /** 공공데이터포털 Encoding 키의 모양을 그대로 흉내 낸 가짜 키. */
    static final String ENCODING_KEY = "AbC%2FdEf%2BgHi%3D%3D";

    /**
     * 빌더와 목 서버를 정적으로 묶어 둔다. {@link TestBean} 의 공급 메서드가 정적이어야
     * 하므로 목 서버도 같은 자리에 있어야 한다. 선언 순서가 곧 결합 순서다 — 목 서버를
     * 만드는 순간 빌더의 요청 팩토리가 가짜로 바뀐다.
     */
    private static final RestClient.Builder BUILDER = RestClient.builder();

    private static final MockRestServiceServer SERVER =
            MockRestServiceServer.bindTo(BUILDER).ignoreExpectOrder(true).build();

    /** 설정 클래스가 만드는 빈을 이름으로 갈아 끼운다. 컨텍스트의 나머지는 그대로다. */
    @TestBean(name = "korServiceRestClientBuilder")
    private RestClient.Builder korServiceRestClientBuilder;

    private static RestClient.Builder korServiceRestClientBuilder() {
        return BUILDER;
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ExternalApiCacheRepository cacheRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void reset() {
        cacheRepository.deleteAll();
        SERVER.reset();
    }

    @Test
    @DisplayName("다른 테스트와 스키마를 공유하지 않는다")
    void usesItsOwnSchema() throws Exception {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            String schema = connection.getCatalog();

            assertThat(schema)
                    .withFailMessage("관통 테스트는 전용 스키마를 써야 한다. 지금 접속한 곳: %s", schema)
                    .endsWith("_through_mock");
        }
    }

    @Test
    @DisplayName("목록 요청이 만드는 공급자 호출에서 serviceKey 가 다시 인코딩되지 않는다")
    void doesNotReEncodeServiceKeyAcrossTheStack() throws Exception {
        SERVER.expect(ExpectedCount.once(), requestTo(Matchers.allOf(
                        Matchers.startsWith(BASE_URL + "/areaBasedList2?serviceKey=" + ENCODING_KEY + "&"),
                        Matchers.not(Matchers.containsString("%252F")),
                        Matchers.containsString("_type=json"))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        mvcList()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.source").value("KorService2"));

        SERVER.verify();
        assertThat(cacheRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("캐시도 없고 공급자도 실패하면 오류가 아니라 정보 없음으로 알린다")
    void providerFailureBecomesNoData() throws Exception {
        SERVER.expect(ExpectedCount.manyTimes(), requestTo(Matchers.any(String.class)))
                .andRespond(withServerError());

        mvcList()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.dataStatus").value("NO_DATA"))
                .andExpect(jsonPath("$.data.items").isEmpty());

        assertThat(cacheRepository.findAll()).isEmpty();
    }

    private ResultActions mvcList() throws Exception {
        return mvc.perform(get("/api/v1/attractions").param("page", "1").param("size", "3"));
    }

    private static String fixture() {
        try (InputStream in = AttractionListMockServerThroughTest.class
                .getResourceAsStream("/fixtures/korservice-areaBasedList2.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("fixture 를 읽지 못했습니다.", e);
        }
    }
}
