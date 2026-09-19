package com.mamoki.tour.global.schedule;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mamoki.tour.domain.mention.repository.OnlineMentionSnapshotRepository;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollectResult;
import com.mamoki.tour.domain.mention.service.OnlineMentionCollector;
import com.mamoki.tour.global.enums.SnapshotStatus;

/**
 * 온라인 언급량을 월 1회 수집한다.
 *
 * <p>{@code --job=mention} 과 같은 수집을 같은 수명주기로 부른다. 전체 수집에 성공했을 때만
 * 새 스냅샷이 활성화되고, 실패하면 직전 활성 스냅샷이 그대로 남는 것은 수집기가 지키는
 * 규칙이라 여기서 다시 다루지 않는다.
 *
 * <p>이 클래스가 더하는 것은 두 가지다.
 *
 * <ol>
 *   <li><b>이미 진행 중이면 부르지 않는다.</b> 운영자가 손으로 돌린 수집이 아직 도는 중에
 *       스케줄이 깨어날 수 있다. 카탈로그 수천 곳을 도는 배치가 겹치면 같은 키로 호출이
 *       두 배가 되어 한도에 걸린다.</li>
 *   <li><b>실패를 스케줄러 밖으로 흘리지 않는다.</b> 실패를 던지면 다음 실행까지의 동작이
 *       스케줄러 오류 처리에 맡겨진다. 무엇이 실패했는지 남기고 다음 달을 기다린다.</li>
 * </ol>
 */
public class MentionCollectSchedule {

    private static final Logger log = LoggerFactory.getLogger(MentionCollectSchedule.class);

    private final OnlineMentionCollector collector;
    private final OnlineMentionSnapshotRepository snapshotRepository;
    private final ZoneId zone;

    /** 스케줄 스레드가 쓰고 다른 스레드가 읽는다. */
    private volatile ScheduledRun lastRun;

    public MentionCollectSchedule(OnlineMentionCollector collector,
                                  OnlineMentionSnapshotRepository snapshotRepository,
                                  ZoneId zone) {
        this.collector = collector;
        this.snapshotRepository = snapshotRepository;
        this.zone = zone;
    }

    public void collect() {
        LocalDateTime startedAt = LocalDateTime.now(zone);

        // 다른 프로세스가 돌리는 수집까지 본다. 운영에서 컨테이너를 따로 띄워 손으로 돌릴 수 있다.
        if (snapshotRepository.existsByStatus(SnapshotStatus.IMPORTING)) {
            String detail = "진행 중인 수집이 있어 건너뜁니다.";
            log.warn("온라인 언급량 수집 스케줄: {}", detail);
            lastRun = ScheduledRun.skipped(startedAt, detail);
            return;
        }

        YearMonth month = YearMonth.now(zone);

        log.info("온라인 언급량 수집 스케줄을 시작합니다: month={}", month);

        try {
            OnlineMentionCollectResult result = collector.collect(month);
            String detail = "version=%s, 대상=%d, 수집=%d"
                    .formatted(result.snapshot().getVersion(), result.target(), result.collected());

            log.info("온라인 언급량 수집 스케줄을 마쳤습니다: {}", detail);
            lastRun = ScheduledRun.completed(startedAt, detail);

        } catch (RuntimeException e) {
            // 실패한 스냅샷과 직전 활성 스냅샷 처리는 수집기가 이미 끝냈다. 여기서는 남기기만 한다.
            log.error("온라인 언급량 수집 스케줄이 실패했습니다: month={}", month, e);
            lastRun = ScheduledRun.failed(startedAt, String.valueOf(e.getMessage()));
        }
    }

    /** 이 프로세스가 뜬 뒤 마지막으로 스케줄이 깨어났을 때의 결과. 한 번도 돌지 않았으면 빈 값. */
    public Optional<ScheduledRun> lastRun() {
        return Optional.ofNullable(lastRun);
    }
}
