package com.mamoki.tour.domain.placemapping.service;

import java.util.Set;

import com.mamoki.tour.domain.placemapping.enums.MappingSource;

/**
 * 원천 이름과 카탈로그를 잇는 단 하나의 진입점.
 *
 * <p>TMAP 적재·입장객 적재·연관 장소 조회가 각자 카탈로그를 읽어 이름으로 잇고 있었다.
 * 매핑 표를 더하면서 그 자리가 셋으로 늘면, 한 곳에서 확정 매핑을 보는 것을 빠뜨려도
 * 값이 그냥 비어 보일 뿐 에러가 나지 않는다. 그래서 "기존 이름 매칭 우선, 없으면 확정
 * 매핑" 이라는 규칙을 이 구현 하나에 둔다.
 *
 * <p>두 방향이 필요하다. 적재는 이름에서 관광지로 가고({@link #index}), 연관 장소 조회는
 * 관광지에서 그 관광지를 가리키는 원천 쪽 이름들로 간다({@link #normalizedAliases}).
 */
public interface PlaceMatcher {

    /**
     * 대량 매칭용 색인을 만든다. 만드는 시점의 카탈로그와 확정 매핑을 담는다.
     *
     * <p>적재 한 번에 한 개만 만들어 쓴다. 행마다 만들면 카탈로그를 행 수만큼 읽는다.
     */
    PlaceMatchIndex index(MappingSource source);

    /**
     * 한 관광지를 가리키는, 그 원천 쪽 표기 이름들(정규화한 값).
     *
     * @param catalogName 카탈로그가 적은 이름. 결과에 항상 먼저 담긴다. 기존 이름 매칭으로
     *                    이어지던 관계는 매핑이 없어도 그대로 유지되어야 한다.
     */
    Set<String> normalizedAliases(MappingSource source, String contentId, String catalogName);
}
