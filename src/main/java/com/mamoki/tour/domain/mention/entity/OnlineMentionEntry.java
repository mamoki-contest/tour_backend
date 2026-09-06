package com.mamoki.tour.domain.mention.entity;

import java.time.LocalDateTime;

import com.mamoki.tour.global.entity.BaseEntity;
import com.mamoki.tour.global.enums.MentionStatus;

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
 * 스냅샷 안의 관광지별 온라인 언급량 한 건.
 *
 * <p>실제 사용한 검색어를 그대로 남긴다. 값이 이상할 때 검색어를 그대로 재현해 봐야
 * 규칙 탓인지 표기 탓인지 가릴 수 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "online_mention_entry",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_online_mention_entry_snapshot_content",
                columnNames = {"snapshot_id", "content_id"}),
        indexes = {
                @Index(name = "idx_online_mention_entry_snapshot", columnList = "snapshot_id"),
                @Index(name = "idx_online_mention_entry_total", columnList = "snapshot_id, mention_total")
        }
)
public class OnlineMentionEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id",
            foreignKey = @ForeignKey(name = "fk_online_mention_entry_snapshot"))
    private OnlineMentionSnapshot snapshot;

    /** 표준 관광지 식별자. KorService2 의 contentId. */
    @Column(name = "content_id", nullable = false, length = 30)
    private String contentId;

    /** 수집 시점의 관광지명. 이후 카탈로그가 바뀌어도 무엇으로 검색했는지 남는다. */
    @Column(name = "place_name", nullable = false, length = 200)
    private String placeName;

    /** 실제 호출에 사용한 검색어. 검색어를 만들지 못했으면 null. */
    @Column(name = "search_query", length = 300)
    private String searchQuery;

    /** 블로그 검색 결과 수. 정상 수집이 아니면 null. 0 으로 채우지 않는다. */
    @Column(name = "mention_total")
    private Long mentionTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MentionStatus status;

    /** 이 값을 수집한 시각. */
    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    /** 정상 수집이 아닐 때의 사유. */
    @Column(name = "note", length = 300)
    private String note;

    @Builder
    private OnlineMentionEntry(OnlineMentionSnapshot snapshot, String contentId, String placeName,
                               String searchQuery, Long mentionTotal, MentionStatus status,
                               LocalDateTime collectedAt, String note) {
        this.snapshot = snapshot;
        this.contentId = contentId;
        this.placeName = placeName;
        this.searchQuery = searchQuery;
        this.mentionTotal = mentionTotal;
        this.status = status;
        this.collectedAt = collectedAt;
        this.note = note;
    }

    /** 정렬에 쓸 수 있는 값인지. 모호·미수집은 정보 없음 구역으로 보낸다. */
    public boolean isSortable() {
        return status == MentionStatus.COLLECTED && mentionTotal != null;
    }
}
