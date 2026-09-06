package com.mamoki.tour.domain.interest.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.mamoki.tour.global.entity.BaseEntity;
import com.mamoki.tour.global.enums.SnapshotStatus;

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
 * 관광지 관심도 CSV 적재 단위.
 *
 * <p>한국관광 데이터랩 공식 파일을 한 번 적재할 때마다 한 행이 생긴다. 적재에 실패한
 * 시도도 이력으로 남기므로, 이 테이블은 곧 적재 이력이기도 하다.
 *
 * <p>조회는 항상 {@link SnapshotStatus#ACTIVE} 스냅샷만 사용한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "interest_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interest_snapshot_version", columnNames = "version"),
        indexes = @Index(name = "idx_interest_snapshot_status", columnList = "status")
)
public class InterestSnapshot extends BaseEntity {

    /** 스냅샷 버전. 응답에 노출해 프론트가 어떤 적재분을 보고 있는지 알 수 있게 한다. */
    @Column(name = "version", nullable = false, length = 40)
    private String version;

    /** 원천 조회기간. 데이터랩 파일이 대상으로 하는 기간(예: 202507). */
    @Column(name = "source_period", nullable = false, length = 20)
    private String sourcePeriod;

    /** 운영자가 파일을 내려받은 날. */
    @Column(name = "downloaded_on", nullable = false)
    private LocalDate downloadedOn;

    /** 우리 DB 에 적재한 시각. 원천 조회기간과 구분한다. */
    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SnapshotStatus status;

    /** 적재한 원본 파일명. 어떤 파일에서 왔는지 추적한다. */
    @Column(name = "source_file_name", nullable = false, length = 300)
    private String sourceFileName;

    /** 적재된 관심도 행 수. */
    @Column(name = "row_count", nullable = false)
    private int rowCount;

    /** 실패 사유. 성공한 스냅샷은 null. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Builder
    private InterestSnapshot(String version, String sourcePeriod, LocalDate downloadedOn,
                             LocalDateTime importedAt, SnapshotStatus status,
                             String sourceFileName, int rowCount, String failureReason) {
        this.version = version;
        this.sourcePeriod = sourcePeriod;
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

    public boolean isActive() {
        return status == SnapshotStatus.ACTIVE;
    }
}
