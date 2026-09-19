package com.mamoki.tour.domain.placemapping.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 시·군의 카탈로그를, 표기를 한 겹 더 걷어낸 이름으로 찾아보는 색인.
 *
 * <p>카카오를 부르지 않는다. 이미 가진 카탈로그를 다르게 읽을 뿐이라 호출 한도를 쓰지 않고
 * 카카오가 그 이름을 모르는 경우에도 선다.
 *
 * <p><b>고르지 않는다.</b> 걷어낸 이름이 같아지는 카탈로그가 둘이면 둘 다 돌려준다.
 * 그중 하나를 고르는 순간, 괄호를 걷어내면 같아지는 서로 다른 장소
 * ({@code 설악산(오색지구)} 와 {@code 설악산(백담지구)}) 에 값이 붙는다.
 */
public final class NameVariantIndex {

    private final String regionName;
    private final Map<String, List<CatalogCandidate>> byVariantName;

    private NameVariantIndex(String regionName, Map<String, List<CatalogCandidate>> byVariantName) {
        this.regionName = regionName;
        this.byVariantName = byVariantName;
    }

    public static NameVariantIndex of(String regionName, List<CatalogCandidate> catalog) {
        Map<String, List<CatalogCandidate>> byVariantName = new HashMap<>();

        for (CatalogCandidate candidate : catalog) {
            String variant = MappingNameNormalizer.normalize(candidate.name(), regionName);

            if (variant != null) {
                byVariantName.computeIfAbsent(variant, key -> new ArrayList<>()).add(candidate);
            }
        }

        return new NameVariantIndex(regionName, byVariantName);
    }

    /**
     * @return 표기만 걷어내면 이 이름과 같아지는 카탈로그 후보들. 없으면 빈 목록이고,
     *         둘 이상이면 좁히지 못한 것이다.
     */
    public List<CatalogCandidate> candidatesFor(String sourceName) {
        String variant = MappingNameNormalizer.normalize(sourceName, regionName);

        if (variant == null) {
            return List.of();
        }

        return List.copyOf(byVariantName.getOrDefault(variant, List.of()));
    }
}
