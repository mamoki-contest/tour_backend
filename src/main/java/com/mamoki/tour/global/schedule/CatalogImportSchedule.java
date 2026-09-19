package com.mamoki.tour.global.schedule;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportResult;
import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportService;

/**
 * 관광지 카탈로그를 주기적으로 다시 적재한다.
 *
 * <p>언급량 수집과 같은 구조지만 <b>기본은 꺼져 있고, 켜는 것을 권하지 않는다.</b> 카탈로그
 * 적재는 공급자에서 수천 건을 받아 한 트랜잭션에 쓰는 작업이라 실패했을 때 사람이 봐야 한다.
 * 실제로 KorService2 는 장애가 잦았다. 운영이 안정되었다고 판단했을 때 켠다.
 *
 * <p>적재기가 상태를 스냅샷으로 나누지 않아 진행 중인지 밖에서 알 수 없다. 대신 적재 전체가
 * 한 트랜잭션이라 실패하면 아무것도 남지 않고, 겹쳐 돌아도 같은 {@code contentId} 를 덮어쓴다.
 */
public class CatalogImportSchedule {

    private static final Logger log = LoggerFactory.getLogger(CatalogImportSchedule.class);

    private final AttractionCatalogImportService importService;
    private final ZoneId zone;

    /** 스케줄 스레드가 쓰고 다른 스레드가 읽는다. */
    private volatile ScheduledRun lastRun;

    public CatalogImportSchedule(AttractionCatalogImportService importService, ZoneId zone) {
        this.importService = importService;
        this.zone = zone;
    }

    public void importCatalog() {
        LocalDateTime startedAt = LocalDateTime.now(zone);

        log.info("관광지 카탈로그 재적재 스케줄을 시작합니다.");

        try {
            AttractionCatalogImportResult result = importService.importAll();
            String detail = "받음=%d, 신규=%d, 갱신=%d, 지역 미매핑=%d"
                    .formatted(result.fetched(), result.inserted(), result.updated(),
                            result.regionUnmapped());

            log.info("관광지 카탈로그 재적재 스케줄을 마쳤습니다: {}", detail);
            lastRun = ScheduledRun.completed(startedAt, detail);

        } catch (RuntimeException e) {
            // 적재가 한 트랜잭션이라 실패하면 직전 카탈로그가 그대로 남는다.
            log.error("관광지 카탈로그 재적재 스케줄이 실패했습니다.", e);
            lastRun = ScheduledRun.failed(startedAt, String.valueOf(e.getMessage()));
        }
    }

    /** 이 프로세스가 뜬 뒤 마지막으로 스케줄이 깨어났을 때의 결과. 한 번도 돌지 않았으면 빈 값. */
    public Optional<ScheduledRun> lastRun() {
        return Optional.ofNullable(lastRun);
    }
}
