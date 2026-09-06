package com.mamoki.tour.infra.naver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mamoki.tour.infra.naver.dto.NaverBlogSearchResponse;

/** 저장한 실제 응답으로 네이버 블로그 검색 계약을 검증한다. */
class NaverBlogSearchContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private NaverBlogSearchResponse fixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/naver-blog-search.json")) {
            return objectMapper.readValue(in, NaverBlogSearchResponse.class);
        }
    }

    @Test
    @DisplayName("정렬 기준으로 쓸 total 을 읽는다")
    void readsTotal() throws Exception {
        NaverBlogSearchResponse response = fixture();

        assertThat(response.hasTotal()).isTrue();
        assertThat(response.total()).isEqualTo(172598L);
    }

    @Test
    @DisplayName("응답의 나머지 필드도 해석한다")
    void readsRemainingFields() throws Exception {
        NaverBlogSearchResponse response = fixture();

        assertThat(response.lastBuildDate()).isNotBlank();
        assertThat(response.display()).isEqualTo(2);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).link()).startsWith("http");
    }

    @Test
    @DisplayName("모르는 필드가 늘어나도 깨지지 않는다")
    void toleratesUnknownFields() throws Exception {
        NaverBlogSearchResponse response = objectMapper.readValue(
                "{\"total\":5,\"newField\":\"x\",\"items\":[]}", NaverBlogSearchResponse.class);

        assertThat(response.total()).isEqualTo(5L);
    }

    @Test
    @DisplayName("total 이 없으면 값을 만들어내지 않는다")
    void doesNotInventTotal() throws Exception {
        NaverBlogSearchResponse response = objectMapper.readValue(
                "{\"display\":1,\"items\":[]}", NaverBlogSearchResponse.class);

        assertThat(response.hasTotal()).isFalse();
        assertThat(response.total()).isNull();
    }
}
