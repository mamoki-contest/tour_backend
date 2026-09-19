package com.mamoki.tour.domain.placemapping.importer;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.placemapping.entity.PlaceMapping;
import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.placemapping.repository.PlaceMappingRepository;
import com.mamoki.tour.domain.placemapping.support.PlaceMappingDecision;

/**
 * 판정 하나를 표에 남긴다.
 *
 * <p>이름 하나마다 트랜잭션을 나눈다. 한도 초과나 인증 실패로 중간에 멈춰도 그때까지 판정한
 * 것은 남아야 한다. 한 트랜잭션으로 묶으면 마지막 호출이 실패한 순간 수천 건의 판정이
 * 통째로 사라지고, 다음 실행이 같은 이름을 처음부터 다시 부른다.
 */
@Service
public class PlaceMappingWriter {

    private final PlaceMappingRepository placeMappingRepository;

    public PlaceMappingWriter(PlaceMappingRepository placeMappingRepository) {
        this.placeMappingRepository = placeMappingRepository;
    }

    /**
     * 같은 (원천, 이름, 시·군) 행이 있으면 갈아 끼우고 없으면 새로 만든다.
     *
     * <p>운영자가 넣은 행은 건드리지 않는다. 사람이 확인해 넣은 판단을 자동 판정이 되돌리면,
     * 같은 행이 실행할 때마다 왔다 갔다 하면서 아무도 알아채지 못한다.
     *
     * @return 실제로 기록했으면 true, 운영자 행이라 건너뛰었으면 false
     */
    @Transactional
    public boolean record(MappingSource source, UnmatchedPlaceName name,
                          PlaceMappingDecision decision, LocalDateTime decidedAt) {

        PlaceMapping existing = placeMappingRepository
                .findBySourceAndNormalizedNameAndLawdCode(
                        source, name.normalizedName(), name.lawdCode())
                .orElse(null);

        if (existing != null && existing.isManual()) {
            return false;
        }

        if (existing != null) {
            existing.redecide(decision, decidedAt);

            return true;
        }

        placeMappingRepository.save(PlaceMapping.decided(source, name.sourceName(),
                name.normalizedName(), name.lawdCode(), decision, decidedAt));

        return true;
    }
}
