package com.mamoki.tour.infra.naver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mamoki.tour.infra.naver.dto.NaverImageSearchResponse;

/**
 * 네이버 이미지 검색 응답 계약.
 *
 * <p>fixture 는 <b>공식 문서의 응답 예시</b>(NAVER API HUB 이미지 검색, 2026-06-11 판)를
 * 옮긴 것이다. 블로그 검색과 달리 실제 응답을 저장하지 못했다 — 우리 Application 에서
 * 이미지 검색이 아직 활성화되지 않아 실호출이 401 로 막힌다(PR 본문 참고). 활성화한 뒤
 * 실응답으로 이 파일을 갈아 끼우면 계약이 문서가 아니라 실제와 맞는지 확인할 수 있다.
 */
class NaverImageSearchContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private NaverImageSearchResponse fixture() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/naver-image-search.json")) {
            return objectMapper.readValue(in, NaverImageSearchResponse.class);
        }
    }

    @Test
    @DisplayName("첫 유효 항목의 원본·썸네일 주소를 읽는다")
    void readsFirstUsableItem() throws Exception {
        NaverImageSearchResponse.Item first = fixture().firstUsable().orElseThrow();

        assertThat(first.imageUrl()).isEqualTo(
                "https://blogfiles.pstatic.net/MjAyNjA2MTFfMjcx/gyeongpo-sunrise.jpg");
        assertThat(first.thumbnailUrl()).startsWith("https://search.pstatic.net/");
    }

    @Test
    @DisplayName("응답의 나머지 필드도 해석한다")
    void readsRemainingFields() throws Exception {
        NaverImageSearchResponse response = fixture();

        assertThat(response.lastBuildDate()).isNotBlank();
        assertThat(response.total()).isEqualTo(25_055_897L);
        assertThat(response.display()).isEqualTo(3);
        assertThat(response.items()).hasSize(3);
        assertThat(response.items().get(0).sizewidth()).isEqualTo("682");
    }

    @Test
    @DisplayName("썸네일이 비면 원본 주소를 썸네일 자리에 쓴다")
    void fallsBackToLinkWhenThumbnailIsBlank() throws Exception {
        NaverImageSearchResponse response = objectMapper.readValue("""
                {"total":1,"items":[{"title":"x","link":"https://a/b.jpg","thumbnail":""}]}
                """, NaverImageSearchResponse.class);

        NaverImageSearchResponse.Item first = response.firstUsable().orElseThrow();

        assertThat(first.thumbnailUrl()).isEqualTo("https://a/b.jpg");
        assertThat(first.imageUrl()).isEqualTo("https://a/b.jpg");
    }

    @Test
    @DisplayName("원본이 비면 썸네일을 원본 자리에 쓴다")
    void fallsBackToThumbnailWhenLinkIsBlank() throws Exception {
        NaverImageSearchResponse response = objectMapper.readValue("""
                {"total":1,"items":[{"title":"x","link":null,"thumbnail":"https://t/c.jpg"}]}
                """, NaverImageSearchResponse.class);

        NaverImageSearchResponse.Item first = response.firstUsable().orElseThrow();

        assertThat(first.imageUrl()).isEqualTo("https://t/c.jpg");
    }

    @Test
    @DisplayName("주소가 하나도 없는 항목은 건너뛰고 다음 항목을 쓴다")
    void skipsItemsWithoutAnyUrl() throws Exception {
        NaverImageSearchResponse response = objectMapper.readValue("""
                {"total":2,"items":[
                  {"title":"빈 항목","link":"","thumbnail":null},
                  {"title":"쓸 수 있는 항목","link":"https://a/d.jpg","thumbnail":"https://t/d.jpg"}
                ]}
                """, NaverImageSearchResponse.class);

        assertThat(response.firstUsable().orElseThrow().imageUrl()).isEqualTo("https://a/d.jpg");
    }

    @Test
    @DisplayName("결과가 0건이면 값을 만들어내지 않는다")
    void doesNotInventAnImage() throws Exception {
        NaverImageSearchResponse response = objectMapper.readValue(
                "{\"total\":0,\"items\":[]}", NaverImageSearchResponse.class);

        assertThat(response.firstUsable()).isEmpty();
    }

    @Test
    @DisplayName("items 가 아예 없어도 0건과 같게 다룬다")
    void toleratesMissingItems() throws Exception {
        NaverImageSearchResponse response = objectMapper.readValue(
                "{\"total\":0}", NaverImageSearchResponse.class);

        assertThat(response.firstUsable()).isEmpty();
    }

    @Test
    @DisplayName("모르는 필드가 늘어나도 깨지지 않는다")
    void toleratesUnknownFields() throws Exception {
        NaverImageSearchResponse response = objectMapper.readValue("""
                {"total":1,"newField":"x","items":[
                  {"link":"https://a/e.jpg","thumbnail":"https://t/e.jpg","newItemField":1}
                ]}
                """, NaverImageSearchResponse.class);

        assertThat(response.firstUsable()).isPresent();
    }
}
