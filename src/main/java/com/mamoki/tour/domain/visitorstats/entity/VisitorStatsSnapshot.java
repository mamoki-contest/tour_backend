package com.mamoki.tour.domain.visitorstats.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.mamoki.tour.global.entity.BaseEntity;
import com.mamoki.tour.global.enums.SnapshotStatus;
import com.mamoki.tour.global.enums.VisitorCountStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주요관광지점 입장객통계 엑셀 적재 단위.
 *
 * <p>관광지식정보시스템 공식 파일을 한 번 적재할 때마다 한 행이 생긴다. 실패한 시도도
 * 이력으로 남는다.
 *
 * <p>{@code publishedMonth} 는 파일 헤더의 마지막 월이 아니라 <b>값이 실제로 들어 있는</b>
 * 마지막 월이다. 이 파일은 아직 공표되지 않은 달의 열을 미리 만들어 두고 비워 놓기 때문에,
 * 헤더를 믿으면 전 관광지가 입장객 0명으로 내려간다.
 *
 * <p>조회는 항상 {@link SnapshotStatus#ACTIVE} 스냅샷만 사용한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "visitor_stats_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_visitor_stats_snapshot_version", columnNames = "version"),
        indexes = @Index(name = "idx_visitor_stats_snapshot_status", columnList = "status")
)
public class VisitorStatsSnapshot extends BaseEntity {

    @Column(name = "version", nullable = false, length = 40)
    private String version;

    /** 값이 실제로 들어 있는 마지막 월(yyyyMM). 응답의 기준 기간으로 내보낸다. */
    @Column(name = "published_month", nullable = false, length = 6)
    private String publishedMonth;

    /** 파일이 담고 있는 전체 기간(예: 202501-202606). 공표월과 구분한다. */
    @Column(name = "source_period", nullable = false, length = 20)
    private String sourcePeriod;

    /** 잠정/확정. 파일에 표시가 없어 공표 규칙으로 판단한 값이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "count_status", nullable = false, length = 20)
    private VisitorCountStatus countStatus;

    /** 운영자가 파일을 내려받은 날. */
    @Column(name = "downloaded_on", nullable = false)
    private LocalDate downloadedOn;

    /** 우리 DB 에 적재한 시각. 공표 기간과 구분한다. */
    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SnapshotStatus status;

    @Column(name = "source_file_name", nullable = false, length = 300)
    private String sourceFileName;

    /** 적재된 행 수. 공표월에 값이 있던 관광지 수다. */
    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Builder
    private VisitorStatsSnapshot(String version, String publishedMonth, String sourcePeriod,
                                 VisitorCountStatus countStatus, LocalDate downloadedOn,
                                 LocalDateTime importedAt, SnapshotStatus status,
                                 String sourceFileName, int rowCount, String failureReason) {
        this.version = version;
        this.publishedMonth = publishedMonth;
        this.sourcePeriod = sourcePeriod;
        this.countStatus = countStatus;
        this.downloadedOn = downloadedOn;
        this.importedAt = importedAt;
        this.status = status;
        this.sourceFileName = sourceFileName;
        this.rowCount = rowCount;
        this.failureReason = failureReason;
    }

    public void activate(int rowCount) {
        this.status = SnapshotStatus.ACTIVE;
        this.rowCount = rowCount;
    }

    public void supersede() {
        this.status = SnapshotStatus.SUPERSEDED;
    }

    public void fail(String reason) {
        this.status = SnapshotStatus.FAILED;
        this.failureReason = reason;
    }
}
