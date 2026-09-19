package com.mamoki.tour.domain.placemapping.importer;

import java.util.List;

import com.mamoki.tour.domain.placemapping.enums.MappingSource;

/**
 * 한 원천에서 아직 카탈로그에 잇지 못한 이름을 모은다.
 *
 * <p>원천마다 이름이 어디에 있는지가 다르다. TMAP·입장객은 활성 스냅샷의 미매칭 행이고,
 * 연관 장소는 스냅샷 없이 공급자 응답의 기준 관광지명이다. 이미 이어진 이름은 담지 않는다.
 * 부르면 얻는 것 없이 하루 한도만 깎는다.
 */
public interface UnmatchedNameCollector {

    MappingSource source();

    /** @return 판정 대상 이름들. 원천 데이터가 아직 없으면 빈 목록이다. */
    List<UnmatchedPlaceName> collect();
}
