package com.mamoki.tour.domain.mention.entity;

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
 * 온라인 언급량 월간 수집 단위.
 *
 * <p>검색어 규칙이 바뀌면 같은 장소의 값도 달라지므로 규칙 버전을 함께 남긴다. 규칙이 다른
 * 스냅샷끼리 값을 비교하면 안 된다.
 *
 * <p>실패한 수집도 이력으로 남는다. 이 테이블이 곧 수집 이력이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "online_mention_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_online_mention_snapshot_version", columnNames = "version"),
        indexes = @Index(name = "idx_online_mention_snapshot_status", columnList = "status")
)
public class OnlineMentionSnapshot extends BaseEntity {

    @Column(name = "version", nullable = false, length = 60)
    private String version;

    /** 수집한 달(yyyyMM). 블로그 검색은 시점 지정이 없어 수집 시점이 곧 기준이다. */
    @Column(name = "collection_month", nullable = false, length = 6)
    private String collectionMonth;

    /** 이 스냅샷을 만든 검색어 규칙. 규칙이 다르면 값을 비교하지 않는다. */
    @Column(name = "query_rule_version", nullable = false, length = 100)
    private String queryRuleVersion;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 수집이 끝난 시각. 진행 중이거나 중단되었으면 null. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SnapshotStatus status;

    /** 수집 대상 관광지 수. */
    @Column(name = "target_count", nullable = false)
    private int targetCount;

    /** 정상 수집된 관광지 수. targetCount 와 다르면 활성화하지 않는다. */
    @Column(name = "collected_count", nullable = false)
    private int collectedCount;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Builder
    private OnlineMentionSnapshot(String version, String collectionMonth, String queryRuleVersion,
                                  LocalDateTime startedAt, SnapshotStatus status, int targetCount) {
        this.version = version;
        this.collectionMonth = collectionMonth;
        this.queryRuleVersion = queryRuleVersion;
        this.startedAt = startedAt;
        this.status = status;
        this.targetCount = targetCount;
        this.collectedCount = 0;
    }

    public void complete(int collectedCount, LocalDateTime completedAt) {
        this.collectedCount = collectedCount;
        this.completedAt = completedAt;
        this.status = SnapshotStatus.ACTIVE;
    }

    public void supersede() {
        this.status = SnapshotStatus.SUPERSEDED;
    }

    public void fail(String reason, int collectedCount, LocalDateTime failedAt) {
        this.status = SnapshotStatus.FAILED;
        this.failureReason = reason;
        this.collectedCount = collectedCount;
        this.completedAt = failedAt;
    }

    public boolean isActive() {
        return status == SnapshotStatus.ACTIVE;
    }
}
