package com.mamoki.tour.global.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportResult;
import com.mamoki.tour.domain.attraction.importer.AttractionCatalogImportService;

/**
 * 관광지 카탈로그 재적재 스케줄.
 *
 * <p>언급량 스케줄과 같은 구조라 여기서도 확인하는 것은 실패를 스케줄러 밖으로 흘리지
 * 않는지다. 적재 자체는 {@code AttractionCatalogImportServiceTest} 가 검증한다.
 */
class CatalogImportScheduleTest {

    private AttractionCatalogImportService importService;
    private CatalogImportSchedule schedule;

    @BeforeEach
    void setUp() {
        importService = Mockito.mock(AttractionCatalogImportService.class);

        given(importService.importAll())
                .willReturn(new AttractionCatalogImportResult(2371, 12, 2359, 4));

        schedule = new CatalogImportSchedule(importService, ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("카탈로그를 적재한다")
    void importsCatalog() {
        schedule.importCatalog();

        Mockito.verify(importService).importAll();
    }

    @Test
    @DisplayName("적재를 마치면 이력에 남긴다")
    void recordsCompletedRun() {
        schedule.importCatalog();

        assertThat(schedule.lastRun()).hasValueSatisfying(run ->
                assertThat(run.outcome()).isEqualTo(ScheduledRun.Outcome.COMPLETED));
    }

    @Test
    @DisplayName("적재가 실패해도 예외를 밖으로 던지지 않는다")
    void doesNotPropagateImportFailure() {
        willThrow(new IllegalStateException("공급자 장애")).given(importService).importAll();

        assertThatCode(() -> schedule.importCatalog()).doesNotThrowAnyException();

        assertThat(schedule.lastRun()).hasValueSatisfying(run -> {
            assertThat(run.outcome()).isEqualTo(ScheduledRun.Outcome.FAILED);
            assertThat(run.detail()).contains("공급자 장애");
        });
    }

    @Test
    @DisplayName("한 번도 돌지 않았으면 이력이 비어 있다")
    void hasNoRunBeforeFirstExecution() {
        assertThat(schedule.lastRun()).isEmpty();
    }
}
