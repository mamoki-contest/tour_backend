package com.mamoki.tour.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mamoki.tour.domain.cache.entity.ExternalApiCache;
import com.mamoki.tour.domain.cache.repository.ExternalApiCacheRepository;
import com.sun.net.httpserver.HttpServer;

/**
 * 컨트롤러에서 DB 까지 한 번에 지나는 관통 테스트.
 *
 * <p>공급자만 가짜로 세우고 나머지는 전부 실제다. 컨트롤러·서비스·캐시 계층·MySQL 이
 * 그대로 참여한다. 확인하려는 것은 <b>공급자 장애가 오류가 아니라 응답의 한 상태로
 * 바뀌어 프론트까지 닿는가</b>다. 네 갈래를 순서대로 지난다.
 *
 * <ol>
 *   <li>공급자 성공 → {@code AVAILABLE}, 캐시에 적재</li>
 *   <li>같은 조회 반복 → 캐시 적중, 공급자를 다시 부르지 않음</li>
 *   <li>캐시가 만료된 뒤 공급자 실패 → {@code STALE} + 예전 수집 시각</li>
 *   <li>캐시도 없고 공급자도 실패 → {@code NO_DATA}, 그래도 HTTP 200</li>
 * </ol>
 *
 * <p>가짜 공급자는 {@code MockRestServiceServer} 가 아니라 실제 HTTP 서버다. 각
 * 클라이언트가 {@code RestClient} 를 생성자 안에서 직접 만들어 쓰기 때문에 밖에서
 * 요청 팩토리를 갈아 끼울 자리가 없다(#66). 주소만 바꿔 끼울 수 있어 이 방법을 택했다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttractionListThroughTest {

    private static final AtomicInteger providerCalls = new AtomicInteger();
    private static final AtomicBoolean providerFails = new AtomicBoolean();

    private static HttpServer provider;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ExternalApiCacheRepository cacheRepository;

    @BeforeAll
    static void startProvider() throws IOException {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/areaBasedList2", exchange -> {
            providerCalls.incrementAndGet();

            if (providerFails.get()) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }

            byte[] body = fixture().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);

            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        provider.start();
    }

    @AfterAll
    static void stopProvider() {
        provider.stop(0);
    }

    @DynamicPropertySource
    static void useFakeProvider(DynamicPropertyRegistry registry) {
        registry.add("tour.api.kor-service.base-url",
                () -> "http://127.0.0.1:" + provider.getAddress().getPort());
        registry.add("tour.api.kor-service.service-key", () -> "test-key");
    }

    @BeforeEach
    void reset() {
        cacheRepository.deleteAll();
        providerCalls.set(0);
        providerFails.set(false);
    }

    @Test
    @DisplayName("공급자 응답이 목록 응답까지 닿고 캐시에 남는다")
    void providerSuccessReachesResponseAndCache() throws Exception {
        mvc.perform(get("/api/v1/attractions").param("page", "1").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.dataStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.totalCount").value(676))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].contentId").value("2868839"))
                .andExpect(jsonPath("$.data.source").value("KorService2"))
                .andExpect(jsonPath("$.data.collectedAt").exists());

        assertThat(providerCalls).hasValue(1);
        assertThat(cacheRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("같은 조회를 다시 하면 캐시로 답하고 공급자를 부르지 않는다")
    void secondCallHitsCache() throws Exception {
        String firstCollectedAt = collectedAtOf(getList());

        // 이제 공급자는 무조건 실패한다. 캐시를 쓰는지 여기서 갈린다.
        providerFails.set(true);

        String secondCollectedAt = collectedAtOf(getList());

        assertThat(providerCalls).hasValue(1);
        // 두 번째 응답은 DB 를 거쳐 오므로 초 아래 자릿수가 마이크로초로 반올림된다.
        // 같은 수집 시각인지만 보면 되므로 초까지 맞춘다.
        assertThat(toSeconds(secondCollectedAt)).isEqualTo(toSeconds(firstCollectedAt));
    }

    @Test
    @DisplayName("캐시가 만료된 뒤 공급자가 실패하면 최종 정상 데이터로 응답한다")
    void staleWhenProviderFailsWithExpiredCache() throws Exception {
        getList();

        LocalDateTime collectedAt = expireCachedEntry();
        providerFails.set(true);

        mvc.perform(get("/api/v1/attractions").param("page", "1").param("size", "3"))
                // 공급자 장애가 오류로 새어 나오지 않는다.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataStatus").value("STALE"))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                // 기준 시점은 마지막으로 정상 수집한 때다. 지금이 아니다.
                .andExpect(jsonPath("$.data.collectedAt").value(startsWithSeconds(collectedAt)));

        assertThat(providerCalls).hasValue(2);
    }

    @Test
    @DisplayName("캐시도 없고 공급자도 실패하면 정보 없음으로 알린다")
    void noDataWhenProviderFailsWithoutCache() throws Exception {
        providerFails.set(true);

        mvc.perform(get("/api/v1/attractions").param("page", "1").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.dataStatus").value("NO_DATA"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                // 값을 얻지 못했으므로 기준 시점도 없다. 지금 시각으로 채우지 않는다.
                .andExpect(jsonPath("$.data.collectedAt").doesNotExist());

        assertThat(cacheRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("한 번 실패해 정보 없음이 된 뒤에도 공급자가 돌아오면 다시 유효해진다")
    void recoversWhenProviderComesBack() throws Exception {
        providerFails.set(true);
        mvc.perform(get("/api/v1/attractions").param("page", "1").param("size", "3"))
                .andExpect(jsonPath("$.data.dataStatus").value("NO_DATA"));

        providerFails.set(false);
        mvc.perform(get("/api/v1/attractions").param("page", "1").param("size", "3"))
                .andExpect(jsonPath("$.data.dataStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.items.length()").value(3));
    }

    // --- 도우미 ---------------------------------------------------------------

    private String getList() throws Exception {
        return mvc.perform(get("/api/v1/attractions").param("page", "1").param("size", "3"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String collectedAtOf(String responseBody) {
        int at = responseBody.indexOf("\"collectedAt\":\"");
        assertThat(at).isNotNegative();

        return responseBody.substring(at + 15, responseBody.indexOf('"', at + 15));
    }

    /** 캐시를 만료시키고 원래 수집 시각을 돌려준다. */
    private LocalDateTime expireCachedEntry() {
        List<ExternalApiCache> entries = cacheRepository.findAll();
        assertThat(entries).hasSize(1);

        ExternalApiCache entry = entries.get(0);
        LocalDateTime collectedAt = entry.getCollectedAt();
        entry.refresh(entry.getResponseBody(), collectedAt, LocalDateTime.now().minusMinutes(1));
        cacheRepository.saveAndFlush(entry);

        return collectedAt;
    }

    /** 초 아래 자릿수를 떼어낸다. */
    private static String toSeconds(String isoDateTime) {
        return isoDateTime.substring(0, "yyyy-MM-ddTHH:mm:ss".length());
    }

    /** JSON 의 시각 표기는 초 아래 자릿수가 달라질 수 있어 초까지만 맞춘다. */
    private static org.hamcrest.Matcher<String> startsWithSeconds(LocalDateTime time) {
        return org.hamcrest.Matchers.startsWith(time.withNano(0).toString());
    }

    private static String fixture() {
        try (InputStream in = AttractionListThroughTest.class
                .getResourceAsStream("/fixtures/korservice-areaBasedList2.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("fixture 를 읽지 못했습니다.", e);
        }
    }
}
