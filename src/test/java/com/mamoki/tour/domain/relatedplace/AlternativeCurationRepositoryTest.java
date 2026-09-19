package com.mamoki.tour.domain.relatedplace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.relatedplace.entity.AlternativeCuration;
import com.mamoki.tour.domain.relatedplace.enums.CurationAction;
import com.mamoki.tour.domain.relatedplace.repository.AlternativeCurationRepository;

/**
 * 대체지 큐레이션 표.
 *
 * <p>시드가 비어 있는 것이 정상이다. 큐레이션은 운영 판단이라 기본값이 없고, 빈 표는
 * "자격 판정 결과를 그대로 쓴다" 는 뜻이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlternativeCurationRepositoryTest {

    private static final String GANGNEUNG = "51150";

    @Autowired
    private AlternativeCurationRepository curationRepository;

    @Test
    @DisplayName("시드는 큐레이션을 하나도 넣지 않는다")
    void seedsNothing() {
        assertThat(curationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("기준 관광지별로 큐레이션을 찾는다")
    void findsByBaseContentId() {
        curationRepository.save(curation("125787", "정동진"));
        curationRepository.save(curation("126508", "주문진항"));

        assertThat(curationRepository.findAllByBaseContentId("125787"))
                .extracting(AlternativeCuration::getTargetNormalizedName)
                .containsExactly("정동진");
    }

    @Test
    @DisplayName("사유 없이는 등록할 수 없다")
    void requiresReason() {
        // 근거 없는 큐레이션이 쌓이면 나중에 지울 수도 고칠 수도 없다.
        AlternativeCuration withoutReason = AlternativeCuration.builder()
                .baseContentId("125787")
                .targetNormalizedName("정동진")
                .targetLawdCode(GANGNEUNG)
                .action(CurationAction.EXCLUDE)
                .build();

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> curationRepository.saveAndFlush(withoutReason))
                .isInstanceOf(Exception.class);
    }

    private static AlternativeCuration curation(String baseContentId, String targetName) {
        return AlternativeCuration.builder()
                .baseContentId(baseContentId)
                .targetNormalizedName(targetName)
                .targetLawdCode(GANGNEUNG)
                .action(CurationAction.EXCLUDE)
                .reason("테마와 맞지 않는 후보")
                .build();
    }
}
