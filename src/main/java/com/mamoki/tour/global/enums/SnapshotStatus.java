package com.mamoki.tour.global.enums;

/**
 * 관심도 스냅샷의 수명 주기.
 *
 * <p>활성 스냅샷은 언제나 최대 하나다. 새 스냅샷은 전체 검증에 성공한 뒤에만
 * {@link #ACTIVE} 가 되며, 실패하면 직전 정상 스냅샷이 그대로 유지된다.
 */
public enum SnapshotStatus {

    /** 적재 중. 아직 조회에 사용하지 않는다. */
    IMPORTING,

    /** 현재 조회에 사용하는 스냅샷. 동시에 하나만 존재한다. */
    ACTIVE,

    /** 더 새로운 스냅샷으로 교체된 이전 스냅샷. 이력으로 남긴다. */
    SUPERSEDED,

    /** 검증 실패로 활성화되지 못한 스냅샷. 적재 이력으로만 남긴다. */
    FAILED
}
