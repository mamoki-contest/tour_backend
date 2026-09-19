package com.mamoki.tour.domain.relatedplace.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.relatedplace.dto.RelatedPlace;
import com.mamoki.tour.domain.relatedplace.entity.AlternativeCuration;
import com.mamoki.tour.domain.relatedplace.repository.AlternativeCurationRepository;
import com.mamoki.tour.domain.relatedplace.support.AlternativeCurations;

/**
 * 대체지 큐레이션을 읽어 적용한다.
 *
 * <p>{@code RelatedPlaceService} 의 <b>추천 자격 판정 뒤</b>에 한 번 불린다. 매칭·자격
 * 판정과 따로 두어, 어느 쪽을 고쳐도 다른 쪽의 경계가 흔들리지 않게 한다.
 *
 * <p>큐레이션 표가 비어 있으면 자격 판정 결과가 그대로 나간다. 빈 표는 정상이다.
 */
@Service
public class AlternativeCurationService {

    private final AlternativeCurationRepository curationRepository;

    public AlternativeCurationService(AlternativeCurationRepository curationRepository) {
        this.curationRepository = curationRepository;
    }

    /**
     * @param baseContentId 상세를 연 기준 관광지의 표준 식별자
     * @param eligible      추천 자격을 충족한 대체지 후보. 여기에 없는 장소는 어떤 큐레이션으로도 들어오지 못한다.
     */
    @Transactional(readOnly = true)
    public List<RelatedPlace> curate(String baseContentId, List<RelatedPlace> eligible) {
        if (baseContentId == null || eligible.isEmpty()) {
            return eligible;
        }

        List<AlternativeCuration> curations = curationRepository.findAllByBaseContentId(baseContentId);

        if (curations.isEmpty()) {
            return eligible;
        }

        return AlternativeCurations.apply(eligible, curations);
    }
}
