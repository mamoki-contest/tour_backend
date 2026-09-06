package com.mamoki.tour.domain.attraction.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.global.entity.BaseEntity;
import com.mamoki.tour.global.enums.DataStatus;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정규화한 관광지 카탈로그.
 *
 * <p>결측 가능한 값은 모두 nullable 로 둔다. 0 이나 빈 문자열로 채우면 `값이 0` 과
 * `정보 없음` 을 구분할 수 없게 되어 PRD 공통 원칙에 어긋난다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "attraction",
        uniqueConstraints = @UniqueConstraint(name = "uk_attraction_content_id", columnNames = "content_id"),
        indexes = {
                @Index(name = "idx_attraction_region", columnList = "region_code_id"),
                @Index(name = "idx_attraction_coordinate", columnList = "latitude, longitude")
        }
)
public class Attraction extends BaseEntity {

    /** 표준 관광지 식별자. KorService2 의 contentId 를 그대로 사용한다. */
    @Column(name = "content_id", nullable = false, length = 30)
    private String contentId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /** 관광지 분류. KorService2 의 contentTypeId. */
    @Column(name = "content_type_id", length = 10)
    private String contentTypeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_code_id", foreignKey = @ForeignKey(name = "fk_attraction_region_code"))
    private RegionCode regionCode;

    /** 시·군 내부 중심관광지 순위. 미산정 장소를 낮은 순위로 취급하지 않도록 nullable. */
    @Column(name = "center_rank")
    private Integer centerRank;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_status", nullable = false, length = 20)
    private DataStatus dataStatus;

    /** 외부 데이터의 기준 시점. 우리 DB 저장 시각(createdAt)과 구분한다. */
    @Column(name = "base_at")
    private LocalDateTime baseAt;

    /** 데이터 출처 공급자 표기. */
    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Builder
    private Attraction(String contentId, String name, String imageUrl, String address,
                       BigDecimal latitude, BigDecimal longitude, String contentTypeId,
                       RegionCode regionCode, Integer centerRank, DataStatus dataStatus,
                       LocalDateTime baseAt, String source) {
        this.contentId = contentId;
        this.name = name;
        this.imageUrl = imageUrl;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.contentTypeId = contentTypeId;
        this.regionCode = regionCode;
        this.centerRank = centerRank;
        this.dataStatus = dataStatus;
        this.baseAt = baseAt;
        this.source = source;
    }
}
