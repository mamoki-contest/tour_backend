package com.mamoki.tour.domain.region.entity;

import com.mamoki.tour.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 강원 18개 시·군의 지역코드 매핑.
 *
 * <p>공급자마다 지역을 가리키는 코드 체계가 달라, 법정동 코드를 기준으로 관광공사
 * 영역 코드를 이어 붙인다.
 *
 * <p>sigunguCode 는 KorService2 의 areaCode2 조회로 확인해야 하는 값이라 아직 비어 있다.
 * 임의 값으로 채우지 않고 API 키 발급 후 채운다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "region_code",
        uniqueConstraints = @UniqueConstraint(name = "uk_region_code_lawd_code", columnNames = "lawd_code"),
        indexes = @Index(name = "idx_region_code_area_sigungu", columnList = "area_code, sigungu_code")
)
public class RegionCode extends BaseEntity {

    /** 법정동 코드 시·군 단위 5자리. 강원특별자치도는 51 로 시작한다. */
    @Column(name = "lawd_code", nullable = false, length = 5)
    private String lawdCode;

    /** 한국관광공사 영역 코드. 강원은 32. */
    @Column(name = "area_code", nullable = false, length = 5)
    private String areaCode;

    /** 한국관광공사 시·군구 코드. 미확인 상태를 구분해야 하므로 nullable 로 둔다. */
    @Column(name = "sigungu_code", length = 5)
    private String sigunguCode;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Builder
    private RegionCode(String lawdCode, String areaCode, String sigunguCode, String name) {
        this.lawdCode = lawdCode;
        this.areaCode = areaCode;
        this.sigunguCode = sigunguCode;
        this.name = name;
    }

    public void assignSigunguCode(String sigunguCode) {
        this.sigunguCode = sigunguCode;
    }
}
