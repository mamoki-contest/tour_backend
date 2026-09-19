package com.mamoki.tour.domain.placemapping.importer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.visitorstats.entity.VisitorStatsEntry;
import com.mamoki.tour.domain.visitorstats.entity.VisitorStatsSnapshot;
import com.mamoki.tour.domain.visitorstats.repository.VisitorStatsEntryRepository;
import com.mamoki.tour.domain.visitorstats.service.VisitorStatsSnapshotService;

/**
 * 활성 입장객통계 스냅샷에서 카탈로그에 잇지 못한 관광지명을 모은다.
 *
 * <p>공식 파일에는 골프장·리조트처럼 관광공사 카탈로그의 대상이 아닌 장소가 섞여 있다
 * (#4 의 2026-09-18 댓글). 그런 이름은 카카오가 찾아 주더라도 이을 카탈로그 후보가 없어
 * {@code UNMATCHED} 로 남는다. 표기 문제와 애초에 대상이 아닌 것이 판정 결과로 갈린다.
 */
@Service
class VisitorStatsUnmatchedNameCollector implements UnmatchedNameCollector {

    private static final Logger log =
            LoggerFactory.getLogger(VisitorStatsUnmatchedNameCollector.class);

    private final VisitorStatsSnapshotService snapshotService;
    private final VisitorStatsEntryRepository entryRepository;
    private final RegionCodeRepository regionCodeRepository;

    VisitorStatsUnmatchedNameCollector(VisitorStatsSnapshotService snapshotService,
                                       VisitorStatsEntryRepository entryRepository,
                                       RegionCodeRepository regionCodeRepository) {
        this.snapshotService = snapshotService;
        this.entryRepository = entryRepository;
        this.regionCodeRepository = regionCodeRepository;
    }

    @Override
    public MappingSource source() {
        return MappingSource.VISITOR_STATS;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnmatchedPlaceName> collect() {
        VisitorStatsSnapshot active = snapshotService.findActive().orElse(null);

        if (active == null) {
            log.warn("활성 입장객통계 스냅샷이 없습니다. 먼저 --job=visitor-stats 로 적재하세요.");
            return List.of();
        }

        Map<String, String> lawdCodes = lawdCodesByRegionName();
        Map<String, UnmatchedPlaceName> byKey = new LinkedHashMap<>();

        for (VisitorStatsEntry entry : entryRepository.findUnmatchedBySnapshot(active)) {
            String regionName = entry.getRawRegionName();
            UnmatchedPlaceName name = UnmatchedPlaceName.of(
                    entry.getRawPlaceName(), lawdCodes.get(regionName), regionName);

            if (name.isDecidable()) {
                byKey.putIfAbsent(name.key(), name);
            }
        }

        return List.copyOf(byKey.values());
    }

    private Map<String, String> lawdCodesByRegionName() {
        Map<String, String> byName = new LinkedHashMap<>();

        for (RegionCode region : regionCodeRepository.findAll()) {
            byName.put(region.getName(), region.getLawdCode());
        }

        return byName;
    }
}
