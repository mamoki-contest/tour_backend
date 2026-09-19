package com.mamoki.tour.domain.placemapping.importer;

import com.mamoki.tour.domain.placemapping.enums.MappingSource;

/**
 * 매핑 배치 한 번의 결과.
 *
 * <p>{@code stoppedReason} 이 있으면 대상 이름을 다 보지 못하고 멈춘 것이다. 끝까지 돈 것과
 * 중간에 멈춘 것을 같은 모양으로 보고하면, 남은 이름이 있는데도 다 했다고 읽힌다.
 *
 * @param targetNames    이번 실행이 볼 대상이었던 이름 수
 * @param skipped        이미 확정했거나 운영자가 넣은 행이라 호출하지 않은 수
 * @param kakaoCalls     실제로 낸 카카오 호출 수
 * @param nameVariant    표기 차이로 되찾아 확정한 이름 수. {@code confirmed} 안에 든 값이다.
 * @param outOfCatalog   카탈로그 대상이 아니라고 본 이름 수
 * @param unknown        어느 쪽인지 모른 채로 남긴 이름 수. 분모에 그대로 있다.
 * @param matchRate      이 원천의 매칭률. 분모 정리 전과 후를 함께 담는다.
 */
public record PlaceMappingResult(
        MappingSource source,
        int targetNames,
        int skipped,
        int kakaoCalls,
        int confirmed,
        int lowConfidence,
        int unmatched,
        int nameVariant,
        int outOfCatalog,
        int unknown,
        SourceMatchRate matchRate,
        String stoppedReason
) {

    /** 키가 없거나 원천 데이터가 없어 아무것도 하지 않은 경우. */
    public static PlaceMappingResult notRun(MappingSource source, String reason) {
        return new PlaceMappingResult(source, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                SourceMatchRate.none(), reason);
    }

    public boolean stoppedEarly() {
        return stoppedReason != null;
    }

    public String summary() {
        String base = ("대상=%d, 건너뜀=%d, 호출=%d, 확정=%d(표기차이=%d), 저신뢰=%d, 미매칭=%d, "
                + "카탈로그밖=%d, 미상=%d")
                .formatted(targetNames, skipped, kakaoCalls, confirmed, nameVariant,
                        lowConfidence, unmatched, outOfCatalog, unknown);

        return stoppedEarly() ? base + ", 중단=" + stoppedReason : base;
    }
}
