package com.mamoki.tour.domain.parking.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.mamoki.tour.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 표준데이터가 말하는 주차장 한 곳.
 *
 * <p><b>실시간 잔여면이 없다.</b> 이 데이터만으로는 "지금 자리가 있는지"에 답할 수 없다.
 * 주차장이 거기 있다는 사실과 규모(주차구획수)까지만 말할 수 있어, 상세 응답에서
 * {@code AVAILABLE} 로 올리지 않는다. 쓰임은 "주차장이 아예 없음" 과 "주차장은 있으나
 * 실시간 정보가 없음" 을 가르는 것이다.
 *
 * <p>좌표가 없는 행도 적재한다. 다만 관광지 반경 조회에는 들어오지 못한다. 좌표를 0 이나
 * 시·군 중심으로 채우면 엉뚱한 관광지에 그 주차장이 붙는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "parking_lot",
        indexes = {
                @Index(name = "idx_parking_lot_snapshot", columnList = "snapshot_id"),
                @Index(name = "idx_parking_lot_box", columnList = "snapshot_id, latitude, longitude")
        }
)
public class ParkingLot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_parking_lot_snapshot"))
    private ParkingLotSnapshot snapshot;

    /** 공급자가 부여한 주차장관리번호. 스냅샷 안에서 유일하다. */
    @Column(name = "management_number", nullable = false, length = 50)
    private String managementNumber;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 공영 / 민영. */
    @Column(name = "category", length = 20)
    private String category;

    /** 노상 / 노외 / 부설. */
    @Column(name = "parking_type", length = 20)
    private String parkingType;

    @Column(name = "road_address", length = 300)
    private String roadAddress;

    @Column(name = "lot_address", length = 300)
    private String lotAddress;

    /** 주차구획수. 0 도 실제 값이다. */
    @Column(name = "capacity", nullable = false)
    private int capacity;

    /** 예: 평일+토요일+공휴일. */
    @Column(name = "operating_days", length = 50)
    private String operatingDays;

    @Column(name = "weekday_open_time", length = 10)
    private String weekdayOpenTime;

    @Column(name = "weekday_close_time", length = 10)
    private String weekdayCloseTime;

    @Column(name = "saturday_open_time", length = 10)
    private String saturdayOpenTime;

    @Column(name = "saturday_close_time", length = 10)
    private String saturdayCloseTime;

    @Column(name = "holiday_open_time", length = 10)
    private String holidayOpenTime;

    @Column(name = "holiday_close_time", length = 10)
    private String holidayCloseTime;

    /** 무료 / 유료 / 혼합. */
    @Column(name = "fee_info", length = 20)
    private String feeInfo;

    /** 좌표가 없으면 null. 0 으로 채우지 않는다. */
    @Column(name = "latitude", precision = 13, scale = 10)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 13, scale = 10)
    private BigDecimal longitude;

    /** 이 행의 공급자 기준일. 시·군마다 다르다. */
    @Column(name = "data_base_date", nullable = false)
    private LocalDate dataBaseDate;

    @Column(name = "provider_code", length = 20)
    private String providerCode;

    /** 예: 강원특별자치도 강릉시. 강원 판정의 근거라 그대로 남긴다. */
    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    @Builder
    private ParkingLot(ParkingLotSnapshot snapshot, String managementNumber, String name,
                       String category, String parkingType, String roadAddress, String lotAddress,
                       int capacity, String operatingDays,
                       String weekdayOpenTime, String weekdayCloseTime,
                       String saturdayOpenTime, String saturdayCloseTime,
                       String holidayOpenTime, String holidayCloseTime,
                       String feeInfo, BigDecimal latitude, BigDecimal longitude,
                       LocalDate dataBaseDate, String providerCode, String providerName) {
        this.snapshot = snapshot;
        this.managementNumber = managementNumber;
        this.name = name;
        this.category = category;
        this.parkingType = parkingType;
        this.roadAddress = roadAddress;
        this.lotAddress = lotAddress;
        this.capacity = capacity;
        this.operatingDays = operatingDays;
        this.weekdayOpenTime = weekdayOpenTime;
        this.weekdayCloseTime = weekdayCloseTime;
        this.saturdayOpenTime = saturdayOpenTime;
        this.saturdayCloseTime = saturdayCloseTime;
        this.holidayOpenTime = holidayOpenTime;
        this.holidayCloseTime = holidayCloseTime;
        this.feeInfo = feeInfo;
        this.latitude = latitude;
        this.longitude = longitude;
        this.dataBaseDate = dataBaseDate;
        this.providerCode = providerCode;
        this.providerName = providerName;
    }

    /** 표시용 주소. 도로명이 비어 있는 행이 많아 지번으로 내려간다. */
    public String displayAddress() {
        return roadAddress != null ? roadAddress : lotAddress;
    }
}
