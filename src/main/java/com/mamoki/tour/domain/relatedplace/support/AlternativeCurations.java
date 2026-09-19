package com.mamoki.tour.domain.relatedplace.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mamoki.tour.domain.attraction.support.PlaceNameNormalizer;
import com.mamoki.tour.domain.relatedplace.dto.RelatedPlace;
import com.mamoki.tour.domain.relatedplace.entity.AlternativeCuration;
import com.mamoki.tour.domain.relatedplace.enums.CurationAction;

/**
 * 추천 자격을 충족한 대체지 후보에 운영 판단을 덧입히는 순수 계산.
 *
 * <p>받는 목록은 <b>이미 자격 판정을 통과한 후보</b>뿐이다. 그래서 큐레이션이 자격 미달
 * 후보를 되살리는 일은 일어날 수 없다. 이 성질은 여기서 다시 검사해 지키는 것이 아니라,
 * 자격 미달 후보가 애초에 이 함수까지 오지 않는 구조로 지킨다.
 *
 * <p>공급자가 표준 관광지 식별자를 주지 않아 대상을 시·군과 정규화한 이름으로 맞춘다.
 * 이름 매칭과 자격 판정은 {@code RelatedPlaceService} 안에 있고, 여기서는 그 결과만
 * 손댄다.
 */
public final class AlternativeCurations {

    private AlternativeCurations() {
    }

    /**
     * 큐레이션을 적용한다.
     *
     * <p>{@code EXCLUDE} 로 빼고, {@code REPRESENTATIVE} 를 맨 앞으로 옮긴다. 그 밖의
     * 상대 순서는 건드리지 않아 공급자 연관 순위가 그대로 남는다. 한 대상에 두 판단이
     * 함께 걸려 있으면 {@code EXCLUDE} 가 이긴다. 부적절하다는 판단과 대표로 삼겠다는
     * 판단이 부딪힐 때는 빼는 쪽이 덜 위험하다.
     *
     * @param eligible  추천 자격을 충족한 후보. 공급자 연관 순위 오름차순.
     * @param curations 이 기준 관광지의 큐레이션. 테마 코드가 적힌 행은 지금 적용되지 않는다.
     */
    public static List<RelatedPlace> apply(List<RelatedPlace> eligible,
                                           List<AlternativeCuration> curations) {

        Map<String, CurationAction> actions = actionsByTarget(curations);

        if (actions.isEmpty()) {
            return eligible;
        }

        List<RelatedPlace> representatives = new ArrayList<>();
        List<RelatedPlace> rest = new ArrayList<>();

        for (RelatedPlace place : eligible) {
            CurationAction action = actions.get(targetKey(place.lawdCode(), place.name()));

            if (action == CurationAction.EXCLUDE) {
                continue;
            }

            if (action == CurationAction.REPRESENTATIVE) {
                representatives.add(place);
            } else {
                rest.add(place);
            }
        }

        List<RelatedPlace> curated = new ArrayList<>(representatives.size() + rest.size());
        curated.addAll(representatives);
        curated.addAll(rest);

        return List.copyOf(curated);
    }

    /**
     * 대상별로 적용할 판단 하나를 정한다.
     *
     * <p>테마 코드가 적힌 행은 건너뛴다. 관광지 상세에는 테마 맥락이 없어서, 적용하면
     * 그 테마 밖에서까지 판단이 새어 나간다.
     */
    private static Map<String, CurationAction> actionsByTarget(List<AlternativeCuration> curations) {
        Map<String, CurationAction> actions = new HashMap<>();

        for (AlternativeCuration curation : curations) {
            if (curation.getThemeCode() != null) {
                continue;
            }

            String key = targetKey(curation.getTargetLawdCode(), curation.getTargetNormalizedName());

            if (key == null) {
                continue;
            }

            actions.merge(key, curation.getAction(),
                    (left, right) -> left == CurationAction.EXCLUDE || right == CurationAction.EXCLUDE
                            ? CurationAction.EXCLUDE
                            : left);
        }

        return actions;
    }

    /**
     * 시·군과 정규화한 이름을 합친 대상 키.
     *
     * <p>저장된 대상 이름도 다시 정규화한다. 정규화는 여러 번 적용해도 같은 값이라
     * 시드가 이미 정규화해 두었어도 안전하고, 손으로 적어 넣은 행이 표기 차이 때문에
     * 조용히 안 맞는 일을 막는다.
     */
    private static String targetKey(String lawdCode, String name) {
        String normalized = PlaceNameNormalizer.normalize(name);

        if (lawdCode == null || normalized == null) {
            return null;
        }

        return lawdCode + ":" + normalized;
    }
}
