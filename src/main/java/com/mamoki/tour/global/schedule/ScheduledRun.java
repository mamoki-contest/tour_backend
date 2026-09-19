package com.mamoki.tour.global.schedule;

import java.time.LocalDateTime;

/**
 * 스케줄이 한 번 깨어났을 때 무슨 일이 있었는지.
 *
 * <p>운영에서 스케줄이 돌았는지 확인할 방법은 로그뿐인데, 컨테이너를 다시 띄우면 로그가
 * 잘린다. 마지막 실행만이라도 프로세스 안에 남겨 둔다. 적재 이력은 각 스냅샷 테이블이
 * 이미 갖고 있으므로 여기에 다시 쌓지 않는다.
 *
 * @param startedAt 깨어난 시각
 * @param outcome   실행 결과
 * @param detail    사람이 읽을 사유. 건너뛴 까닭이나 실패 메시지가 들어간다
 */
public record ScheduledRun(LocalDateTime startedAt, Outcome outcome, String detail) {

    public enum Outcome {

        /** 작업을 불렀고 예외 없이 끝났다. */
        COMPLETED,

        /** 부르지 않았다. 이미 진행 중인 작업이 있는 경우다. */
        SKIPPED,

        /** 불렀으나 작업이 실패했다. 직전 활성 스냅샷은 그대로 남는다. */
        FAILED
    }

    public static ScheduledRun completed(LocalDateTime startedAt, String detail) {
        return new ScheduledRun(startedAt, Outcome.COMPLETED, detail);
    }

    public static ScheduledRun skipped(LocalDateTime startedAt, String detail) {
        return new ScheduledRun(startedAt, Outcome.SKIPPED, detail);
    }

    public static ScheduledRun failed(LocalDateTime startedAt, String detail) {
        return new ScheduledRun(startedAt, Outcome.FAILED, detail);
    }
}
