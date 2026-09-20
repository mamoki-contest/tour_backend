package com.mamoki.tour.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpServer;

/**
 * 진짜 서블릿 컨테이너를 지나는 요청.
 *
 * <p>여기서만 잡히는 것이 있다(#96). 깨진 퍼센트 인코딩({@code %C0%C0} 은 UTF-8 로 풀 수 없는
 * 바이트다)은 <b>톰캣이 쿼리를 푸는 단계</b>에서 터진다. {@code MockMvc} 의
 * {@code standaloneSetup} 은 그 단계를 지나지 않아 파라미터가 그냥 들어가고, 그래서 단위
 * 테스트는 모두 통과하는데 실제 서버만 500 을 돌려주는 일이 생긴다. 실제로 그랬다.
 *
 * <p>잘못된 바이트를 보낸 것은 클라이언트다. 서버 내부 오류로 답하면 프론트는 재시도하고
 * 운영자는 서버 로그를 뒤지게 된다.
 *
 * <p>이 테스트는 {@code --job} 없이 뜬 프로세스가 <b>웹 서버</b>라는 사실도 함께 지킨다(#95).
 * 실제 포트가 열려야 이 요청을 보낼 수 있다.
 *
 * <p>스키마는 이 테스트 전용이다({@link Schema}). 컨텍스트를 하나 더 만드는 이상
 * {@code create-drop} 도 한 번 더 도는데, 그 대상이 다른 테스트가 쓰는 스키마면 안 된다(#86).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = BrokenQueryEncodingThroughTest.Schema.class)
class BrokenQueryEncodingThroughTest {

    /** 공유 스키마 이름에 이 접미어를 붙인 스키마를 쓴다. 없으면 만든다. */
    static class Schema extends ThroughTestSchemaInitializer {
        Schema() {
            super("_encoding");
        }
    }

    private static HttpServer provider;

    @LocalServerPort
    private int port;

    /** 정상 요청이 공급자까지 닿는 갈래를 보려면 답해 줄 공급자가 있어야 한다. */
    @BeforeAll
    static void startProvider() throws IOException {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/searchKeyword2", exchange -> {
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

    @Test
    @DisplayName("깨진 퍼센트 인코딩은 클라이언트 오류(400)로 답한다")
    void brokenPercentEncodingIsBadRequest() {
        // %C0%C0 은 UTF-8 로 풀 수 없는 바이트다. 톰캣이 쿼리를 푸는 단계에서 터진다.
        HttpResponse<String> response = get("/api/v1/attractions/search?query=%C0%C0");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"statusCode\":400");
        // 내부 사정을 드러내지 않는다. 예외 이름도 스택트레이스도 나가지 않는다.
        assertThat(response.body()).doesNotContain("Exception", "decoding");
    }

    @Test
    @DisplayName("제대로 인코딩한 한글 검색어는 그대로 200 이다")
    void properlyEncodedKoreanIsStillOk() {
        // 강릉: UTF-8 퍼센트 인코딩. 400 이 이쪽까지 번지면 검색 자체가 막힌다.
        HttpResponse<String> response = get("/api/v1/attractions/search?query=%EA%B0%95%EB%A6%89");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"resultCode\":\"200-1\"");
    }

    /**
     * 쿼리 문자열을 <b>있는 그대로</b> 보낸다.
     *
     * <p>퍼센트 인코딩을 다시 손대는 클라이언트를 거치면 {@code %25C0%25C0} 이 되어 깨진
     * 인코딩이 아니라 평범한 문자열이 된다. 그러면 재현되지 않는다.
     */
    private HttpResponse<String> get(String pathAndQuery) {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + pathAndQuery)).GET().build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("요청을 보내지 못했습니다.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("요청이 중단됐습니다.", e);
        }
    }

    private static String fixture() {
        try (InputStream in = BrokenQueryEncodingThroughTest.class
                .getResourceAsStream("/fixtures/korservice-searchKeyword2.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("fixture 를 읽지 못했습니다.", e);
        }
    }
}
