package com.mamoki.tour.domain.placemapping.importer;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankEntry;
import com.mamoki.tour.domain.tmaprank.entity.TmapRankSnapshot;
import com.mamoki.tour.domain.tmaprank.repository.TmapRankEntryRepository;
import com.mamoki.tour.domain.tmaprank.service.TmapRankSnapshotService;

/**
 * 활성 TMAP 스냅샷에서 카탈로그에 잇지 못한 관광지명을 모은다.
 *
 * <p>활성 스냅샷이 없으면 빈 목록이다. 적재하지 않은 것과 미매칭이 없는 것은 다르지만,
 * 둘 다 이번 실행에서 부를 이름이 없다는 뜻이라 여기서는 같게 다룬다. 어느 쪽인지는
 * 배치 로그가 대상 수 0 으로 알린다.
 */
@Service
class TmapUnmatchedNameCollector implements UnmatchedNameCollector {

    private static final Logger log = LoggerFactory.getLogger(TmapUnmatchedNameCollector.class);

    private final TmapRankSnapshotService snapshotService;
    private final TmapRankEntryRepository entryRepository;
    private final RegionCodeRepository regionCodeRepository;

    TmapUnmatchedNameCollector(TmapRankSnapshotService snapshotService,
                               TmapRankEntryRepository entryRepository,
                               RegionCodeRepository regionCodeRepository) {
        this.snapshotService = snapshotService;
        this.entryRepository = entryRepository;
        this.regionCodeRepository = regionCodeRepository;
    }

    @Override
    public MappingSource source() {
        return MappingSource.TMAP;
    }

    @Override
    @Transactional(readOnly = true)
    public CollectedNames collect() {
        TmapRankSnapshot active = snapshotService.findActive().orElse(null);

        if (active == null) {
            log.warn("활성 TMAP 스냅샷이 없습니다. 먼저 --job=tmap 으로 적재하세요.");
            return CollectedNames.empty();
        }

        Map<String, String> lawdCodes = lawdCodesByRegionName();
        CollectedNames.Builder collected = new CollectedNames.Builder()
                .rows((int) entryRepository.countBySnapshot(active));

        for (String contentId : entryRepository.findMatchedContentIds(active)) {
            collected.matched(contentId);
        }

        Map<String, Integer> unknownRegions = new LinkedHashMap<>();

        for (TmapRankEntry entry : entryRepository.findUnmatchedBySnapshot(active)) {
            String regionName = entry.getRawRegionName();
            String lawdCode = lawdCodes.get(regionName);

            if (lawdCode == null) {
                unknownRegions.merge(String.valueOf(regionName), 1, Integer::sum);
            }

            collected.unmatched(UnmatchedPlaceName.of(
                    entry.getRawPlaceName(), lawdCode, regionName));
        }

        if (!unknownRegions.isEmpty()) {
            // 시·군을 모르면 카카오 검색을 그 주변으로 좁힐 수도, 카탈로그 후보를 그 시·군으로
            // 한정할 수도 없어 판정 자체를 하지 않는다. 그 행들은 분류되지 않은 채 분모에
            // 남으므로, 매칭률이 낮은 이유가 표기 문제가 아니라 지역 표기 불일치일 수 있다.
            log.warn("TMAP 지역 표기가 시드에 없어 판정하지 않은 행이 있습니다: {}. "
                            + "region_code 시드의 시·군명과 파일의 표기를 맞추세요.",
                    unknownRegions);
        }

        return collected.build();
    }

    private Map<String, String> lawdCodesByRegionName() {
        Map<String, String> byName = new LinkedHashMap<>();

        for (RegionCode region : regionCodeRepository.findAll()) {
            byName.put(region.getName(), region.getLawdCode());
        }

        return byName;
    }
}
