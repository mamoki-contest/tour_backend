package com.mamoki.tour.domain.interest.entity;

import java.math.BigDecimal;

import com.mamoki.tour.global.entity.BaseEntity;
import com.mamoki.tour.global.enums.InterestMatchStatus;

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
 * 스냅샷 안의 관광지별 관심도 한 행.
 *
 * <p>원본 파일의 값(rawRegionName, rawPlaceName)을 그대로 보존한 뒤 매칭 결과를 따로 둔다.
 * 매칭에 실패했다고 행을 버리지 않는다. 무엇이 매칭되지 않았는지가 다음 적재의 단서가 된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "attraction_interest",
        indexes = {
                @Index(name = "idx_attraction_interest_snapshot", columnList = "snapshot_id"),
                @Index(name = "idx_attraction_interest_content", columnList = "snapshot_id, content_id"),
                @Index(name = "idx_attraction_interest_value", columnList = "snapshot_id, interest_value")
        }
)
public class AttractionInterest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id",
            foreignKey = @ForeignKey(name = "fk_attraction_interest_snapshot"))
    private InterestSnapshot snapshot;

    /** 원본 파일의 지역명. 가공하지 않는다. */
    @Column(name = "raw_region_name", nullable = false, length = 100)
    private String rawRegionName;

    /** 원본 파일의 관광지명. 가공하지 않는다. */
    @Column(name = "raw_place_name", nullable = false, length = 300)
    private String rawPlaceName;

    /** 매칭에 사용한 정규화 이름. */
    @Column(name = "normalized_name", nullable = false, length = 300)
    private String normalizedName;

    /** 정렬용 관심도 값. 다른 신호와 합산하지 않는다. */
    @Column(name = "interest_value", nullable = false, precision = 20, scale = 4)
    private BigDecimal interestValue;

    /** 원본 파일의 순위. 파일이 제공하지 않으면 null. */
    @Column(name = "source_rank")
    private Integer sourceRank;

    /** 매칭된 표준 관광지 식별자. 미매칭·저신뢰이면 null. */
    @Column(name = "content_id", length = 30)
    private String contentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 20)
    private InterestMatchStatus matchStatus;

    @Builder
    private AttractionInterest(InterestSnapshot snapshot, String rawRegionName, String rawPlaceName,
                               String normalizedName, BigDecimal interestValue, Integer sourceRank,
                               String contentId, InterestMatchStatus matchStatus) {
        this.snapshot = snapshot;
        this.rawRegionName = rawRegionName;
        this.rawPlaceName = rawPlaceName;
        this.normalizedName = normalizedName;
        this.interestValue = interestValue;
        this.sourceRank = sourceRank;
        this.contentId = contentId;
        this.matchStatus = matchStatus;
    }

    /** 확정 매칭된 행만 정렬에 쓴다. 저신뢰·미매칭은 관심도 산정값으로 확정하지 않는다. */
    public boolean isUsableForSorting() {
        return matchStatus == InterestMatchStatus.MATCHED && contentId != null;
    }
}
