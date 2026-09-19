package com.mamoki.tour.domain.placemapping.service;

import java.util.Map;
import java.util.Optional;

import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.placemapping.enums.MappingSource;

/**
 * 원천 이름을 카탈로그 식별자로 옮기는, 한 번 만들어 두고 쓰는 색인.
 *
 * <p><b>기존 이름 매칭이 먼저고, 없을 때만 확정 매핑을 본다.</b> 이름으로 바로 이어지는
 * 장소는 지금까지와 똑같이 이어져야 한다. 매핑이 앞에 서면 카카오가 한 번 잘못 고른 결과가
 * 멀쩡히 이어지던 이름을 덮어쓰는데, 값이 그럴듯해서 아무도 알아채지 못한다.
 *
 * <p>스냅샷 적재는 수천 행을 돌며 이름을 묻는다. 행마다 질의하면 카탈로그 크기만큼 질의가
 * 늘어나므로 한 번에 읽어 메모리에 둔다.
 */
public final class PlaceMatchIndex {

    private final MappingSource source;
    private final Map<String, String> catalogByName;
    private final Map<String, String> catalogByRegionAndName;
    private final Map<String, String> mappingByName;
    private final Map<String, String> mappingByRegionAndName;

    PlaceMatchIndex(MappingSource source,
                    Map<String, String> catalogByName,
                    Map<String, String> catalogByRegionAndName,
                    Map<String, String> mappingByName,
                    Map<String, String> mappingByRegionAndName) {
        this.source = source;
        this.catalogByName = Map.copyOf(catalogByName);
        this.catalogByRegionAndName = Map.copyOf(catalogByRegionAndName);
        this.mappingByName = Map.copyOf(mappingByName);
        this.mappingByRegionAndName = Map.copyOf(mappingByRegionAndName);
    }

    /**
     * @param regionName 원천이 적은 시·군명. 원천의 매칭 범위가 이름뿐이면 쓰이지 않는다.
     * @param rawName    원천이 적은 이름.
     * @return 이어 붙일 카탈로그 식별자. 이름으로도 매핑으로도 찾지 못하면 빈 값이다.
     */
    public Optional<String> match(String regionName, String rawName) {
        String normalized = PlaceNameNormalizer.normalize(rawName);

        if (normalized == null) {
            return Optional.empty();
        }

        return switch (source.matchScope()) {
            case NAME_ONLY -> first(catalogByName.get(normalized), mappingByName.get(normalized));
            case REGION_AND_NAME -> {
                String key = key(regionName, normalized);

                yield key == null ? Optional.empty()
                        : first(catalogByRegionAndName.get(key), mappingByRegionAndName.get(key));
            }
        };
    }

    /** 이 색인이 쓸 수 있는 확정 매핑 수. 적재 로그가 매핑이 실제로 실린 것을 알리는 데 쓴다. */
    public int mappingCount() {
        return source.matchScope() == MappingSource.MatchScope.NAME_ONLY
                ? mappingByName.size() : mappingByRegionAndName.size();
    }

    static String key(String regionName, String normalizedName) {
        if (regionName == null || regionName.isBlank() || normalizedName == null) {
            return null;
        }

        return regionName.strip() + "|" + normalizedName;
    }

    private static Optional<String> first(String byCatalog, String byMapping) {
        return Optional.ofNullable(byCatalog != null ? byCatalog : byMapping);
    }
}
