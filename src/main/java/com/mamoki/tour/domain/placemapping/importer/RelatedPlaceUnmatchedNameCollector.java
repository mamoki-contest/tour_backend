package com.mamoki.tour.domain.placemapping.importer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    /**
     * <p><b>읽기 전용 트랜잭션으로 묶지 않는다.</b> 공급자 응답을 처음 받는 시·군은 캐시에
     * 그 응답을 적는다. 읽기만 한다고 보고 묶으면 첫 실행이 캐시를 적는 순간 통째로 실패한다.
     * 이름을 읽는 질의들은 각자 자기 트랜잭션에서 돈다.
     */
    @Override
    public CollectedNames collect() {
        PlaceMatchIndex matchIndex = placeMatcher.index(MappingSource.RELATED_PLACE);
        CollectedNames.Builder collected = new CollectedNames.Builder();
        Set<String> seenBaseNames = new LinkedHashSet<>();
        int rows = 0;

        for (RegionCode region : regionCodeRepository.findAll()) {
            List<RelatedPlaceRow> regionRows = regionLoader.load(region.getLawdCode()).rows();

            if (regionRows.isEmpty()) {
                log.warn("연관 장소 응답이 비어 있습니다. lawdCode={}", region.getLawdCode());
                continue;
            }

            for (RelatedPlaceRow row : regionRows) {
                // 기준 관광지명만 본다. 연관 장소명은 그 자체로 조회 대상이 아니라
                // 기준 관광지의 상세에 딸려 나가는 값이다.
                //
                // 한 기준 관광지가 연관 장소 수십 개와 함께 수십 행으로 온다. 그 행 수를
                // 분모로 쓰면 연관 장소를 많이 가진 곳이 분모를 지배한다. 여기서 한 "행" 은
                // (시·군, 기준 관광지명) 하나다.
                if (!seenBaseNames.add(region.getLawdCode() + "|" + row.baseName())) {
                    continue;
                }

                rows++;

                String matched = matchIndex.match(region.getName(), row.baseName()).orElse(null);

                if (matched != null) {
                    collected.matched(matched);
                    continue;
                }

                collected.unmatched(UnmatchedPlaceName.of(
                        row.baseName(), region.getLawdCode(), region.getName()));
            }
        }

        return collected.rows(rows).build();
    }
}
