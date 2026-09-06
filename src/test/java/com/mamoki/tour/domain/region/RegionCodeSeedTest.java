package com.mamoki.tour.domain.region;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;

@SpringBootTest
@ActiveProfiles("test")
class RegionCodeSeedTest {

    @Autowired
    private RegionCodeRepository regionCodeRepository;

    @Test
    @DisplayName("강원 18개 시·군 지역코드가 적재된다")
    void seedsAllGangwonRegions() {
        List<RegionCode> regions = regionCodeRepository.findAllByAreaCode("32");

        assertThat(regions).hasSize(18);
        assertThat(regions).extracting(RegionCode::getName).contains("춘천시", "강릉시", "양양군");
    }

    @Test
    @DisplayName("법정동 코드로 시·군을 조회할 수 있다")
    void findsByLawdCode() {
        assertThat(regionCodeRepository.findByLawdCode("51150"))
                .isPresent()
                .get()
                .extracting(RegionCode::getName)
                .isEqualTo("강릉시");
    }

    @Test
    @DisplayName("확인되지 않은 시·군구 코드는 임의 값 없이 비어 있다")
    void sigunguCodeStaysUnknown() {
        List<RegionCode> regions = regionCodeRepository.findAllByAreaCode("32");

        assertThat(regions).allSatisfy(region -> assertThat(region.getSigunguCode()).isNull());
    }
}
