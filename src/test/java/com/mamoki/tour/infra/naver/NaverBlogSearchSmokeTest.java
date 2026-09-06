package com.mamoki.tour.infra.naver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mamoki.tour.infra.naver.dto.NaverBlogSearchResponse;

/**
 * 실제 네이버 API HUB 를 호출하는 스모크 테스트.
 * {@code RUN_EXTERNAL_API_TEST=true} 일 때만 실행된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_EXTERNAL_API_TEST", matches = "true")
class NaverBlogSearchSmokeTest {

    @Autowired
    private NaverBlogSearchClient client;

    @Test
    @DisplayName("실제 검색어로 total 을 받아온다")
    void fetchesTotal() {
        NaverBlogSearchResponse response = client.search("경포해변 강릉시");

        assertThat(response.hasTotal()).isTrue();
        assertThat(response.total()).isPositive();
    }

    @Test
    @DisplayName("존재하지 않는 이름은 0 건으로 응답한다 - AND 검색임을 확인")
    void unknownNameReturnsZero() {
        NaverBlogSearchResponse response = client.search("흐르는돌비늘폭포 양구군");

        assertThat(response.total()).isZero();
    }
}
