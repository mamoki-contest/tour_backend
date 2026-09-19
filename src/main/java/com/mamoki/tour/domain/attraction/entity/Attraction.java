package com.mamoki.tour.domain.attraction.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /**
     * 좌표 컬럼이 담는 소수 자릿수.
     *
     * <p>아래 두 컬럼의 {@code scale} 과 같은 값이어야 한다. 이 상수가 유일한 출처가 되도록
     * 좌표를 받는 자리마다 {@link #toColumnScale(BigDecimal)} 을 거친다.
     */
    private static final int COORDINATE_SCALE = 7;

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
        this.latitude = toColumnScale(latitude);
        this.longitude = toColumnScale(longitude);
        this.contentTypeId = contentTypeId;
        this.regionCode = regionCode;
        this.centerRank = centerRank;
        this.dataStatus = dataStatus;
        this.baseAt = baseAt;
        this.source = source;
    }

    /**
     * 공급자에서 다시 받아온 값으로 갱신한다.
     *
     * <p>{@code contentId} 는 바꾸지 않는다. 개인 컬렉션(#9)과 TMAP·입장객 매칭이 이 값을
     * 붙들고 있어, 바뀌면 저장해 둔 장소가 끊긴다.
     *
     * <p>중심관광지 순위는 건드리지 않는다. 카탈로그와 적재 주기가 다른 별도 신호다.
     *
     * <p>좌표는 컬럼 자릿수로 맞춰서 들인다. 공급자가 주는 값은 소수 10자리인데 컬럼은
     * 7자리라, 그대로 두면 DB 에서 읽은 값과 매번 달라 내용이 같은 재적재에서도 전 행이
     * UPDATE 된다(#77).
     */
    public void refresh(String name, String imageUrl, String address,
                        BigDecimal latitude, BigDecimal longitude, String contentTypeId,
                        RegionCode regionCode, DataStatus dataStatus, LocalDateTime baseAt) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.address = address;
        this.latitude = toColumnScale(latitude);
        this.longitude = toColumnScale(longitude);
        this.contentTypeId = contentTypeId;
        this.regionCode = regionCode;
        this.dataStatus = dataStatus;
        this.baseAt = baseAt;
    }

    /**
     * 좌표를 컬럼이 담는 자릿수로 맞춘다.
     *
     * <p>어차피 저장하는 순간 DB 가 잘라 내는 자리다. 여기서 미리 맞춰 두어야 다음 적재가
     * 읽어 온 값과 같은 것을 같다고 볼 수 있다. 버림이 아니라 반올림하는 것은 MySQL 이
     * {@code DECIMAL} 에 쓸 때 하는 일과 같게 두기 위해서다 — 다르면 저장한 값과 들고 있는
     * 값이 어긋나 같은 문제가 한 자리 아래에서 되풀이된다.
     *
     * <p>소수 7자리는 위도 약 1cm 다. 지도 표시와 장소 매칭(#55) 어느 쪽에도 뜻이 없는 자리라
     * 잃는 정밀도가 없다.
     *
     * @return 좌표가 없으면 null. 0 으로 채우지 않는다.
     */
    private static BigDecimal toColumnScale(BigDecimal coordinate) {
        return coordinate == null ? null : coordinate.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }
}
