package com.mamoki.tour.domain.placeimage.importer;

/**
 * 대표 사진 보강 배치 한 번의 결과.
 *
 * <p>{@code stoppedReason} 이 있으면 대상을 다 보지 못하고 멈춘 것이다. 끝까지 돈 것과
 * 중간에 멈춘 것을 같은 모양으로 보고하면, 남은 관광지가 있는데도 다 했다고 읽힌다.
 *
 * @param targets 이번 실행이 볼 대상이었던 관광지 수(공급자 사진이 없는 곳)
 * @param skipped 이미 물어본 적이 있어 부르지 않은 수
 * @param calls   실제로 낸 이미지 검색 호출 수. 실패한 호출도 한도를 깎으므로 함께 센다.
 * @param filled  쓸 수 있는 사진을 찾아 채운 수
 * @param none    물었지만 결과가 없던 수. 실패가 아니다.
 * @param failed  이 관광지 하나의 호출·해석이 실패한 수
 */
public record PlaceImageResult(
        int targets,
        int skipped,
        int calls,
        int filled,
        int none,
        int failed,
        String stoppedReason
) {

    /** 키가 없거나 대상이 없어 아무것도 하지 않은 경우. */
    public static PlaceImageResult notRun(String reason) {
        return new PlaceImageResult(0, 0, 0, 0, 0, 0, reason);
    }

    public boolean stoppedEarly() {
        return stoppedReason != null;
    }

    public String summary() {
        String base = "대상=%d, 건너뜀=%d, 호출=%d, 보강=%d, 결과없음=%d, 실패=%d"
                .formatted(targets, skipped, calls, filled, none, failed);

        return stoppedEarly() ? base + ", 중단=" + stoppedReason : base;
    }
}
