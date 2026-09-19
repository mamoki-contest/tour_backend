package com.mamoki.tour.domain.placemapping.importer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.placemapping.service.PlaceMatchIndex;
import com.mamoki.tour.domain.placemapping.service.PlaceMatcher;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.relatedplace.dto.RelatedPlaceRow;
import com.mamoki.tour.domain.relatedplace.service.RelatedPlaceRegionLoader;

/**
 * 연관 장소 공급자가 적은 기준 관광지명 중 카탈로그에 잇지 못한 것을 모은다.
 *
 * <p>다른 둘과 달리 스냅샷이 없다. 조회 시점에 공급자를 부르는 구조라 여기서도 시·군마다
 * 공급자 응답을 한 번씩 읽는다. 24시간 캐시를 쓰므로 상세 조회가 이미 읽어 둔 시·군은
 * 추가 호출이 없다.
 *
 * <p>미매칭이 큰 원천이다. 강릉시 675건 중 연관 데이터가 붙는 것이 10건(1.5%)이었다(#7).
 */
@Service
class RelatedPlaceUnmatchedNameCollector implements UnmatchedNameCollector {

    private static final Logger log =
            LoggerFactory.getLogger(RelatedPlaceUnmatchedNameCollector.class);

    private final RelatedPlaceRegionLoader regionLoader;
    private final RegionCodeRepository regionCodeRepository;
    private final PlaceMatcher placeMatcher;

    RelatedPlaceUnmatchedNameCollector(RelatedPlaceRegionLoader regionLoader,
                                       RegionCodeRepository regionCodeRepository,
                                       PlaceMatcher placeMatcher) {
        this.regionLoader = regionLoader;
        this.regionCodeRepository = regionCodeRepository;
        this.placeMatcher = placeMatcher;
    }

    @Override
    public MappingSource source() {
        return MappingSource.RELATED_PLACE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnmatchedPlaceName> collect() {
        PlaceMatchIndex matchIndex = placeMatcher.index(MappingSource.RELATED_PLACE);
        Map<String, UnmatchedPlaceName> byKey = new LinkedHashMap<>();

        for (RegionCode region : regionCodeRepository.findAll()) {
            List<RelatedPlaceRow> rows = regionLoader.load(region.getLawdCode()).rows();

            if (rows.isEmpty()) {
                log.warn("연관 장소 응답이 비어 있습니다. lawdCode={}", region.getLawdCode());
                continue;
            }

            for (RelatedPlaceRow row : rows) {
                // 기준 관광지명만 본다. 연관 장소명은 그 자체로 조회 대상이 아니라
                // 기준 관광지의 상세에 딸려 나가는 값이다.
                if (matchIndex.match(region.getName(), row.baseName()).isPresent()) {
                    continue;
                }

                UnmatchedPlaceName name = UnmatchedPlaceName.of(
                        row.baseName(), region.getLawdCode(), region.getName());

                if (name.isDecidable()) {
                    byKey.putIfAbsent(name.key(), name);
                }
            }
        }

        return List.copyOf(byKey.values());
    }
}
