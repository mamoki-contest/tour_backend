package com.mamoki.tour.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 허용 출처를 채우면 그 출처가 실제로 허용되는지 본다.
 *
 * <p>{@link CorsDisabledWhenOriginsBlankTest} 와 짝이다. "비우면 꺼진다" 만 지키면
 * CORS 를 통째로 지워도 통과하므로, 채웠을 때 켜지는 쪽도 함께 못 박는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "CORS_ALLOWED_ORIGINS=https://tour.example")
class CorsEnabledWhenOriginsSetTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("허용 출처를 채우면 그 출처로 CORS 매핑이 등록된다")
    void registersCorsMappingForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/attractions")
                        .header("Origin", "https://tour.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://tour.example"));
    }
}
