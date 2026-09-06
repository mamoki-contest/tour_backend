package com.mamoki.tour.domain.tmaprank.entity;

import java.math.BigDecimal;

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
 * 스냅샷 안의 관광지별 TMAP 검색순위 한 행.
 *
 * <p>원본 파일의 값(rawRegionName, rawPlaceName)을 그대로 보존한 뒤 매칭 결과를 따로 둔다.
 * 매칭에 실패했다고 행을 버리지 않는다. 무엇이 매칭되지 않았는지가 다음 적재의 단서가 된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "tmap_rank_entry",
        indexes = {
                @Index(name = "idx_tmap_rank_entry_snapshot", columnList = "snapshot_id"),
                @Index(name = "idx_tmap_rank_entry_content", columnList = "snapshot_id, content_id"),
                @Index(name = "idx_tmap_rank_entry_ratio", columnList = "snapshot_id, search_ratio")
        }
)
public class TmapRankEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id",
            foreignKey = @ForeignKey(name = "fk_tmap_rank_entry_snapshot"))
    private TmapRankSnapshot snapshot;

    /** 원본 파일의 지역명. 가공하지 않는다. */
    @Column(name = "raw_region_name", nullable = false, length = 100)
    private String rawRegionName;

    /** 원본 파일의 관광지명. 가공하지 않는다. */
    @Column(name = "raw_place_name", nullable = false, length = 300)
    private String rawPlaceName;

    /** 매칭에 사용한 정규화 이름. */
    @Column(name = "normalized_name", nullable = false, length = 300)
    private String normalizedName;

    /** 시·군 안에서의 검색 비중(%). 시·군을 넘어 비교하지 않으며 다른 신호와 합산하지 않는다. */
    @Column(name = "search_ratio", nullable = false, precision = 20, scale = 4)
    private BigDecimal searchRatio;

    /** 원본 파일의 순위. 파일이 제공하지 않으면 null. */
    @Column(name = "source_rank")
    private Integer sourceRank;

    /** 매칭된 표준 관광지 식별자. 미매칭·저신뢰이면 null. */
    @Column(name = "content_id", length = 30)
    private String contentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 20)
    private CatalogMatchStatus matchStatus;

    @Builder
    private TmapRankEntry(TmapRankSnapshot snapshot, String rawRegionName, String rawPlaceName,
                               String normalizedName, BigDecimal searchRatio, Integer sourceRank,
                               String contentId, CatalogMatchStatus matchStatus) {
        this.snapshot = snapshot;
        this.rawRegionName = rawRegionName;
        this.rawPlaceName = rawPlaceName;
        this.normalizedName = normalizedName;
        this.searchRatio = searchRatio;
        this.sourceRank = sourceRank;
        this.contentId = contentId;
        this.matchStatus = matchStatus;
    }

    /** 확정 매칭된 행만 보조 근거로 노출한다. 저신뢰·미매칭은 순위를 붙이지 않는다. */
    public boolean isMatched() {
        return matchStatus == CatalogMatchStatus.MATCHED && contentId != null;
    }
}
