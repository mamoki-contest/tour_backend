package com.mamoki.tour.infra.korservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.infra.korservice.dto.KorServiceResponse;

/**
 * 실제 KorService2 를 호출하는 스모크 테스트.
 *
 * <p>PRD 테스트 결정에 따라 일반 테스트마다 외부 API 를 호출하지 않는다.
 * {@code RUN_EXTERNAL_API_TEST=true} 일 때만 실행되며, 인증·필수 필드·기준 시점만 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_EXTERNAL_API_TEST", matches = "true")
class KorServiceClientSmokeTest {

    @Autowired
    private KorServiceClient korServiceClient;

    @Test
    @DisplayName("강원 관광지 목록을 실제로 조회한다")
    void fetchesGangwonAttractions() {
        KorServiceResponse response = korServiceClient.areaBasedList("32", null, null, 1, 5);

        assertThat(response.response().header().isSuccess()).isTrue();
        assertThat(response.totalCount()).isPositive();

        List<AttractionSnapshot> snapshots = KorServiceItemConverter.convertAll(response.items());

        assertThat(snapshots).isNotEmpty();
        assertThat(snapshots).allSatisfy(snapshot -> {
            assertThat(snapshot.contentId()).isNotBlank();
            assertThat(snapshot.name()).isNotBlank();
            assertThat(snapshot.source()).isEqualTo("KorService2");
        });
    }

    @Test
    @DisplayName("강원 시·군 코드 18개를 조회한다")
    void fetchesGangwonSigunguCodes() {
        KorServiceResponse response = korServiceClient.areaCode("32", 50);

        assertThat(response.response().header().isSuccess()).isTrue();
        assertThat(response.totalCount()).isEqualTo(18);
    }
}
