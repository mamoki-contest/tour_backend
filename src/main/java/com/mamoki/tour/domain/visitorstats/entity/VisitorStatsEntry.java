package com.mamoki.tour.domain.visitorstats.entity;

import com.mamoki.tour.global.entity.BaseEntity;
import com.mamoki.tour.global.enums.CatalogMatchStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공표월의 관광지 한 곳 입장객 수.
 *
 * <p>공표월에 값이 없던 관광지는 아예 행을 만들지 않는다. 0 으로 채우면 미집계와 실제 0 명이
 * 구분되지 않는다. 파일에는 두 경우가 모두 있다.
 *
 * <p>{@code visitorCount} 는 내국인·외국인을 더한 `합계` 행의 값을 그대로 옮긴 것이다.
 * 우리가 다시 더하지 않는다. 외국인 행이 아예 없는 관광지도 합계 행은 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "visitor_stats_entry",
        indexes = {
                @Index(name = "idx_visitor_stats_entry_snapshot", columnList = "snapshot_id"),
                @Index(name = "idx_visitor_stats_entry_content", columnList = "content_id")
        }
)
public class VisitorStatsEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_visitor_stats_entry_snapshot"))
    private VisitorStatsSnapshot snapshot;

    /** 파일의 시·군명. 매칭 근거를 그대로 남긴다. */
    @Column(name = "raw_region_name", nullable = false, length = 50)
    private String rawRegionName;

    /** 파일의 관광지명. 표시에는 카탈로그 이름을 쓰고 이 값은 추적용으로만 둔다. */
    @Column(name = "raw_place_name", nullable = false, length = 200)
    private String rawPlaceName;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    /** 공표월 입장객 수. 0 도 실제 값이다. */
    @Column(name = "visitor_count", nullable = false)
    private long visitorCount;

    /** 매칭된 표준 관광지 식별자. 미매칭이면 null. */
    @Column(name = "content_id", length = 20)
    private String contentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 20)
    private CatalogMatchStatus matchStatus;

    @Builder
    private VisitorStatsEntry(VisitorStatsSnapshot snapshot, String rawRegionName,
                              String rawPlaceName, String normalizedName, long visitorCount,
                              String contentId, CatalogMatchStatus matchStatus) {
        this.snapshot = snapshot;
        this.rawRegionName = rawRegionName;
        this.rawPlaceName = rawPlaceName;
        this.normalizedName = normalizedName;
        this.visitorCount = visitorCount;
        this.contentId = contentId;
        this.matchStatus = matchStatus;
    }

    /** 확정 매칭된 행만 보조 근거로 노출한다. 미매칭은 어느 관광지의 값인지 말할 수 없다. */
    public boolean isMatched() {
        return matchStatus == CatalogMatchStatus.MATCHED && contentId != null;
    }
}
