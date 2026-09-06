package com.mamoki.tour.domain.attraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.global.enums.DataStatus;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttractionRepositoryTest {

    @Autowired
    private AttractionRepository attractionRepository;

    @Autowired
    private RegionCodeRepository regionCodeRepository;

    @Test
    @DisplayName("지역코드와 함께 관광지를 저장하고 표준 식별자로 조회한다")
    void saveAndFindByContentId() {
        RegionCode gangneung = regionCodeRepository.findByLawdCode("51150").orElseThrow();

        attractionRepository.save(attraction("126508", "경포해수욕장", gangneung,
                new BigDecimal("37.7952000"), new BigDecimal("128.9086000")));

        Attraction found = attractionRepository.findByContentId("126508").orElseThrow();

        assertThat(found.getName()).isEqualTo("경포해수욕장");
        assertThat(found.getRegionCode().getName()).isEqualTo("강릉시");
    }

    @Test
    @DisplayName("결측 값은 0 이 아니라 null 로 보존된다")
    void missingValuesStayNull() {
        RegionCode sokcho = regionCodeRepository.findByLawdCode("51210").orElseThrow();

        Attraction saved = attractionRepository.save(Attraction.builder()
                .contentId("999999")
                .name("좌표 없는 장소")
                .regionCode(sokcho)
                .dataStatus(DataStatus.NO_DATA)
                .source("KorService2")
                .build());

        assertThat(saved.getCenterRank()).isNull();
        assertThat(saved.getLatitude()).isNull();
        assertThat(saved.getLongitude()).isNull();
        assertThat(saved.getDataStatus()).isEqualTo(DataStatus.NO_DATA);
    }

    @Test
    @DisplayName("지도 경계로 관광지를 조회한다")
    void findWithinBoundingBox() {
        RegionCode gangneung = regionCodeRepository.findByLawdCode("51150").orElseThrow();
        RegionCode chuncheon = regionCodeRepository.findByLawdCode("51110").orElseThrow();

        attractionRepository.save(attraction("1", "강릉 안", gangneung,
                new BigDecimal("37.7952000"), new BigDecimal("128.9086000")));
        attractionRepository.save(attraction("2", "춘천 밖", chuncheon,
                new BigDecimal("37.8813000"), new BigDecimal("127.7300000")));

        List<Attraction> found = attractionRepository.findAllByLatitudeBetweenAndLongitudeBetween(
                new BigDecimal("37.7000000"), new BigDecimal("37.9000000"),
                new BigDecimal("128.8000000"), new BigDecimal("129.0000000"));

        assertThat(found).extracting(Attraction::getName).containsExactly("강릉 안");
    }

    @Test
    @DisplayName("저장된 식별자 목록으로 일괄 조회한다")
    void findAllByContentIdIn() {
        RegionCode yangyang = regionCodeRepository.findByLawdCode("51830").orElseThrow();

        attractionRepository.save(attraction("10", "하조대", yangyang, null, null));
        attractionRepository.save(attraction("11", "낙산사", yangyang, null, null));

        List<Attraction> found = attractionRepository.findAllByContentIdIn(List.of("10", "11", "없는id"));

        assertThat(found).hasSize(2);
    }

    private Attraction attraction(String contentId, String name, RegionCode regionCode,
                                  BigDecimal latitude, BigDecimal longitude) {
        return Attraction.builder()
                .contentId(contentId)
                .name(name)
                .regionCode(regionCode)
                .latitude(latitude)
                .longitude(longitude)
                .dataStatus(DataStatus.AVAILABLE)
                .baseAt(LocalDateTime.now())
                .source("KorService2")
                .build();
    }
}
