package com.mamoki.tour.domain.parking.entity;

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
 * 전국주차장정보표준데이터 적재 단위.
 *
 * <p>공식 CSV 를 한 번 적재할 때마다 한 행이 생긴다. 실패한 시도도 이력으로 남는다.
 * 갱신은 반기(6개월)라 자주 바뀌지 않는다.
 *
 * <p>조회는 항상 {@link SnapshotStatus#ACTIVE} 스냅샷만 사용한다. 입장객통계·TMAP 순위와
 * 같은 수명주기다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "parking_lot_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_parking_lot_snapshot_version", columnNames = "version"),
        indexes = @Index(name = "idx_parking_lot_snapshot_status", columnList = "status")
)
public class ParkingLotSnapshot extends BaseEntity {

    @Column(name = "version", nullable = false, length = 40)
    private String version;

    /**
     * 적재한 강원 행 중 가장 새로운 데이터기준일자.
     *
     * <p>파일 하나에 시·군마다 다른 기준일이 섞여 있다. 시·군별 기준일은 각 주차장 행이
     * 따로 갖고, 여기에는 스냅샷 전체를 대표하는 가장 새로운 날만 둔다.
     */
    @Column(name = "data_base_date", nullable = false)
    private LocalDate dataBaseDate;

    /** 운영자가 파일을 내려받은 날. */
    @Column(name = "downloaded_on", nullable = false)
    private LocalDate downloadedOn;

    /** 우리 DB 에 적재한 시각. 공급자 기준일과 구분한다. */
    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SnapshotStatus status;

    @Column(name = "source_file_name", nullable = false, length = 300)
    private String sourceFileName;

    /** 적재된 강원 주차장 수. */
    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Builder
    private ParkingLotSnapshot(String version, LocalDate dataBaseDate, LocalDate downloadedOn,
                               LocalDateTime importedAt, SnapshotStatus status,
                               String sourceFileName, int rowCount, String failureReason) {
        this.version = version;
        this.dataBaseDate = dataBaseDate;
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
