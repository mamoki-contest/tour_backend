package com.mamoki.tour.domain.placemapping.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mamoki.tour.domain.placemapping.enums.MappingSource;
import com.mamoki.tour.domain.placemapping.enums.PlaceMappingStatus;
import com.mamoki.tour.domain.placemapping.enums.PlaceMatchMethod;
import com.mamoki.tour.global.entity.BaseEntity;

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
 * 원천 이름 하나와 카탈로그 관광지를 잇는 매핑.
 *
 * <p>확정하지 못한 판정도 행으로 남긴다. 미매칭과 저신뢰를 지우면 다음 실행이 같은 이름을
 * 또 부르면서도 지난번에 무엇이 걸렸는지 모른다. 남은 행은 운영자가 {@code MANUAL} 로
 * 고쳐 넣을 자리이기도 하다.
 *
 * <p><b>{@code CONFIRMED} 만 조회에 쓴다.</b> 저신뢰 매칭으로 숫자를 만들지 않는다는 PRD
 * 원칙이 이 한 줄에 걸려 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "place_mapping",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_place_mapping_source_name",
                columnNames = {"source", "normalized_name", "lawd_code"}),
        indexes = {
                @Index(name = "idx_place_mapping_source_status", columnList = "source, status"),
                @Index(name = "idx_place_mapping_content", columnList = "source, content_id")
        }
)
public class PlaceMapping extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private MappingSource source;

    /** 원천이 적은 이름. 가공하지 않는다. 무엇을 보고 판정했는지가 여기 남는다. */
    @Column(name = "source_name", nullable = false, length = 300)
    private String sourceName;

    /** 매칭 키로 쓰는 정규화 이름. 같은 장소를 두 번 판정하지 않도록 유니크 제약에 들어간다. */
    @Column(name = "normalized_name", nullable = false, length = 300)
    private String normalizedName;

    /**
     * 법정동 시·군 코드 5자리.
     *
     * <p>시·군을 알 수 없는 원천 이름도 판정 이력은 남겨야 해서 nullable 로 둔다.
     * MySQL 은 유니크 제약에서 null 을 서로 다른 값으로 보므로, 시·군을 모르는 이름은
     * 같은 이름이라도 행이 나뉜다. 그래도 판정 결과는 같아 조회에 영향이 없다.
     */
    @Column(name = "lawd_code", length = 5)
    private String lawdCode;

    /** 이어 붙인 카탈로그 식별자. 확정이 아니면 가장 가까웠던 후보이거나 null 이며 조회에 쓰지 않는다. */
    @Column(name = "content_id", length = 30)
    private String contentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 20)
    private PlaceMatchMethod method;

    /** 판정 근거의 여유(0~1). 확정 여부는 {@code status} 로 정해지며 이 값으로 정하지 않는다. */
    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    /** 카카오 장소 id. 같은 장소를 나중에 다시 확인할 때 쓰는 추적용 값이다. */
    @Column(name = "kakao_place_id", length = 30)
    private String kakaoPlaceId;

    @Column(name = "kakao_place_name", length = 300)
    private String kakaoPlaceName;

    @Column(name = "kakao_latitude", precision = 10, scale = 7)
    private BigDecimal kakaoLatitude;

    @Column(name = "kakao_longitude", precision = 10, scale = 7)
    private BigDecimal kakaoLongitude;

    /** 카카오 좌표와 카탈로그 좌표 사이 거리(m). 이름으로 확정했거나 좌표가 없으면 null. */
    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlaceMappingStatus status;

    /** 사람이 읽을 판정 근거 한 줄. */
    @Column(name = "reason", length = 500)
    private String reason;

    /** 판정한 시각. 외부 데이터의 기준 시점이 아니라 우리가 판단한 시점이다. */
    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    @Builder
    private PlaceMapping(MappingSource source, String sourceName, String normalizedName,
                         String lawdCode, String contentId, PlaceMatchMethod method,
                         BigDecimal confidence, String kakaoPlaceId, String kakaoPlaceName,
                         BigDecimal kakaoLatitude, BigDecimal kakaoLongitude,
                         Double distanceMeters, PlaceMappingStatus status, String reason,
                         LocalDateTime decidedAt) {
        this.source = source;
        this.sourceName = sourceName;
        this.normalizedName = normalizedName;
        this.lawdCode = lawdCode;
        this.contentId = contentId;
        this.method = method;
        this.confidence = confidence;
        this.kakaoPlaceId = kakaoPlaceId;
        this.kakaoPlaceName = kakaoPlaceName;
        this.kakaoLatitude = kakaoLatitude;
        this.kakaoLongitude = kakaoLongitude;
        this.distanceMeters = distanceMeters;
        this.status = status;
        this.reason = reason;
        this.decidedAt = decidedAt;
    }

    /**
     * 같은 이름을 다시 판정한 결과로 갈아 끼운다.
     *
     * <p>원천·이름·시·군은 이 행을 찾은 키라 바뀌지 않는다. 판정에서 나온 값만 바뀐다.
     */
    public void redecide(String contentId, PlaceMatchMethod method, BigDecimal confidence,
                         String kakaoPlaceId, String kakaoPlaceName,
                         BigDecimal kakaoLatitude, BigDecimal kakaoLongitude,
                         Double distanceMeters, PlaceMappingStatus status, String reason,
                         LocalDateTime decidedAt) {
        this.contentId = contentId;
        this.method = method;
        this.confidence = confidence;
        this.kakaoPlaceId = kakaoPlaceId;
        this.kakaoPlaceName = kakaoPlaceName;
        this.kakaoLatitude = kakaoLatitude;
        this.kakaoLongitude = kakaoLongitude;
        this.distanceMeters = distanceMeters;
        this.status = status;
        this.reason = reason;
        this.decidedAt = decidedAt;
    }

    /**
     * 배치가 건드리면 안 되는 행인지.
     *
     * <p>사람이 확인해 넣은 판단을 자동 판정이 되돌리면, 같은 행이 실행할 때마다 왔다 갔다
     * 하면서 아무도 알아채지 못한다.
     */
    public boolean isManual() {
        return method == PlaceMatchMethod.MANUAL;
    }

    /** 조회에 값을 만들 수 있는 행인지. */
    public boolean isUsable() {
        return status == PlaceMappingStatus.CONFIRMED && contentId != null;
    }
}
