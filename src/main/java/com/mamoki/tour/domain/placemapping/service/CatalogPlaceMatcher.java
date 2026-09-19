package com.mamoki.tour.domain.placemapping.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.placemapping.entity.PlaceMapping;
import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.placemapping.repository.PlaceMappingRepository;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;

/**
 * 카탈로그와 확정 매핑을 읽어 매칭을 한 곳에서 처리한다.
 *
 * <p>매핑 표는 시·군을 법정동 코드로 들고 있고 원천 파일들은 시·군명으로 들고 있다.
 * 이 둘을 여기서 맞춘다. 시드에 없는 법정동 코드는 시·군명을 만들어 내지 않고 이름만으로
 * 찾는 쪽에만 담는다.
 */
@Service
public class CatalogPlaceMatcher implements PlaceMatcher {

    private final AttractionRepository attractionRepository;
    private final PlaceMappingRepository placeMappingRepository;
    private final RegionCodeRepository regionCodeRepository;

    public CatalogPlaceMatcher(AttractionRepository attractionRepository,
                               PlaceMappingRepository placeMappingRepository,
                               RegionCodeRepository regionCodeRepository) {
        this.attractionRepository = attractionRepository;
        this.placeMappingRepository = placeMappingRepository;
        this.regionCodeRepository = regionCodeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PlaceMatchIndex index(MappingSource source) {
        Map<String, String> catalogByName = new HashMap<>();
        Map<String, String> catalogByRegionAndName = new HashMap<>();

        for (Attraction attraction : attractionRepository.findAllWithRegion()) {
            String normalized = PlaceNameNormalizer.normalize(attraction.getName());

            if (normalized == null) {
                continue;
            }

            // 같은 이름이 여럿이면 먼저 담긴 쪽을 쓴다. 적재 때부터의 규칙을 그대로 둔다.
            catalogByName.putIfAbsent(normalized, attraction.getContentId());

            String regionName = attraction.getRegionCode() == null
                    ? null : attraction.getRegionCode().getName();
            String key = PlaceMatchIndex.key(regionName, normalized);

            if (key != null) {
                catalogByRegionAndName.putIfAbsent(key, attraction.getContentId());
            }
        }

        Map<String, String> regionNames = regionNamesByLawdCode();
        Map<String, String> mappingByName = new HashMap<>();
        Map<String, String> mappingByRegionAndName = new HashMap<>();
        Set<String> ambiguousNames = new HashSet<>();
        Set<String> ambiguousRegionKeys = new HashSet<>();

        for (PlaceMapping mapping : placeMappingRepository.findConfirmedBySource(source)) {
            put(mappingByName, ambiguousNames,
                    mapping.getNormalizedName(), mapping.getContentId());
            put(mappingByRegionAndName, ambiguousRegionKeys,
                    PlaceMatchIndex.key(regionNames.get(mapping.getLawdCode()),
                            mapping.getNormalizedName()),
                    mapping.getContentId());
        }

        return new PlaceMatchIndex(source, catalogByName, catalogByRegionAndName,
                mappingByName, mappingByRegionAndName);
    }

    /**
     * 같은 이름이 서로 다른 관광지를 가리키면 그 이름을 아예 뺀다.
     *
     * <p>매핑은 시·군별로 판정하므로, 이름만으로 찾는 원천(TMAP)에서는 시·군이 다른 두
     * 확정 행이 같은 이름을 두고 부딪칠 수 있다. 먼저 담긴 쪽을 쓰면 어느 시·군 행이
     * 먼저 읽혔느냐가 곧 판단이 되어 버린다. 좁히지 못한 것은 값을 만들지 않는다.
     */
    private static void put(Map<String, String> target, Set<String> ambiguous,
                            String key, String contentId) {
        if (key == null || ambiguous.contains(key)) {
            return;
        }

        String existing = target.get(key);

        if (existing == null) {
            target.put(key, contentId);
            return;
        }

        if (!existing.equals(contentId)) {
            target.remove(key);
            ambiguous.add(key);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> normalizedAliases(MappingSource source, String contentId, String catalogName) {
        Set<String> aliases = new LinkedHashSet<>();

        String normalized = PlaceNameNormalizer.normalize(catalogName);

        if (normalized != null) {
            aliases.add(normalized);
        }

        if (contentId == null) {
            return aliases;
        }

        placeMappingRepository.findConfirmedBySourceAndContentId(source, contentId).stream()
                .map(PlaceMapping::getNormalizedName)
                .forEach(aliases::add);

        return aliases;
    }

    private Map<String, String> regionNamesByLawdCode() {
        List<RegionCode> regions = regionCodeRepository.findAll();

        return regions.stream().collect(Collectors.toMap(
                RegionCode::getLawdCode, RegionCode::getName, (left, right) -> left,
                () -> new HashMap<>(regions.size())));
    }
}
