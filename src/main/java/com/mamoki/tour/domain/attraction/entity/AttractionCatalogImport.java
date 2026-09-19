package com.mamoki.tour.domain.attraction.entity;

import java.time.LocalDateTime;

import com.mamoki.tour.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관광지 카탈로그 적재 이력. 한 번 적재할 때마다 한 행이 생긴다(#69).
 *
 * <p><b>왜 따로 남기는가.</b> 카탈로그 적재는 이미 있는 행에 {@code refresh()} 만 부르고,
 * 값이 같으면 Hibernate 가 UPDATE 를 내지 않는다. 그래서 {@code attraction.modified_at} 의
 * 최댓값은 "내용이 마지막으로 바뀐 시각"이지 "적재를 마지막으로 돌린 시각"이 아니다. 내용이
 * 그대로인 재적재를 아무리 돌려도 그 값은 움직이지 않는다. 목록 응답의 {@code collectedAt}
 * 이 묻는 것은 뒤쪽이라, 실행 시각은 이 표가 답한다.
 *
 * <p><b>담기는 것은 끝까지 마친 적재뿐이다.</b> 적재 전체가 한 트랜잭션이고 이 행도 같은
 * 트랜잭션에서 쓰이므로, 실패한 적재는 카탈로그 변경과 함께 이 행까지 통째로 사라진다.
 * 뒤집으면 <b>여기 남은 시각에는 그 카탈로그가 실제로 DB 에 있었다</b>는 뜻이라,
 * {@code collectedAt} 이 필요로 하는 "마지막 성공 적재"가 곧 이 표의 마지막 행이다.
 * 실패한 시도를 보려면 적재 로그를 본다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "attraction_catalog_import",
        indexes = @Index(name = "idx_attraction_catalog_import_completed_at",
                columnList = "completed_at")
)
public class AttractionCatalogImport extends BaseEntity {

    /** 적재를 시작한 시각. 공급자에서 받아오기 전이다. */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * 적재를 마친 시각. 목록 응답의 {@code collectedAt} 이 쓰는 값이다.
     *
     * <p>시작 시각이 아니라 완료 시각을 쓴다. 강원 전체 적재가 수십 초 걸리는 동안에도
     * 사용자가 보는 것은 직전 카탈로그라, 새 내용이 실제로 보이기 시작한 시점이 여기다.
     */
    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    /** 공급자에서 받아온 관광지 수. */
    @Column(name = "fetched_count", nullable = false)
    private int fetchedCount;

    /** 새로 담았거나 갱신한 수. 받은 수와 다르면 중간에 빠진 것이 있다. */
    @Column(name = "saved_count", nullable = false)
    private int savedCount;

    /** 법정동 매핑이 없어 지역을 비워 둔 수. 적재는 되었다. */
    @Column(name = "region_unmapped_count", nullable = false)
    private int regionUnmappedCount;

    @Builder
    private AttractionCatalogImport(LocalDateTime startedAt, LocalDateTime completedAt,
                                    int fetchedCount, int savedCount, int regionUnmappedCount) {
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.fetchedCount = fetchedCount;
        this.savedCount = savedCount;
        this.regionUnmappedCount = regionUnmappedCount;
    }
}
