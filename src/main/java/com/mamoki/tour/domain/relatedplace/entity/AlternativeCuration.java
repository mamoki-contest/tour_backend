package com.mamoki.tour.domain.relatedplace.entity;

import com.mamoki.tour.domain.relatedplace.enums.CurationAction;
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
 * 대체지 큐레이션 한 건. 테이블 {@code alternative_curation}.
 *
 * <p>기준 관광지 하나의 대체지 후보 목록에서 특정 장소를 빼거나 대표로 앞세운다.
 * <b>추천 자격 판정이 끝난 뒤</b>에만 적용되므로, 자격 미달인 후보를 이 표로 되살릴 수는
 * 없다(CONTEXT `대체지 큐레이션`).
 *
 * <p>대상을 표준 관광지 식별자가 아니라 <b>정규화한 이름 + 시·군</b>으로 가리킨다.
 * 연관 장소 공급자({@code TarRlteTarService1})가 식별자를 주지 않아서다. 쓰이지 않을
 * {@code contentId} 컬럼을 미리 두면 어느 쪽으로 대상을 적어야 하는지가 흐려진다.
 * 매핑 테이블이 생기면(#55) 그때 대상 표현을 함께 옮긴다.
 *
 * <p>운영자 화면이 PRD 범위 밖이라 등록은 시드 또는 직접 INSERT 로 한다. 시드는 비어
 * 있는 것이 정상이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "alternative_curation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_alternative_curation_target",
                columnNames = {"base_content_id", "target_lawd_code", "target_normalized_name", "theme_code"}),
        indexes = @Index(name = "idx_alternative_curation_base", columnList = "base_content_id")
)
public class AlternativeCuration extends BaseEntity {

    /** 기준 관광지의 표준 관광지 식별자. 이 관광지의 상세를 열었을 때만 적용된다. */
    @Column(name = "base_content_id", nullable = false, length = 20)
    private String baseContentId;

    /** 대상 연관 장소명을 {@code PlaceNameNormalizer} 로 정규화한 값. */
    @Column(name = "target_normalized_name", nullable = false, length = 100)
    private String targetNormalizedName;

    /**
     * 대상 연관 장소의 법정동 시·군 코드 5자리.
     *
     * <p>같은 이름이 여러 시·군에 있어 시·군 없이는 대상을 특정할 수 없다. 넓게 잡히는
     * 실수를 막으려고 필수로 둔다.
     */
    @Column(name = "target_lawd_code", nullable = false, length = 5)
    private String targetLawdCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private CurationAction action;

    /**
     * 이 판단이 적용될 지원 테마 코드. 비면 테마와 무관하게 적용한다.
     *
     * <p>관광지 상세({@code GET /api/v1/attractions/{contentId}})에는 테마 맥락이 없다.
     * 그래서 <b>테마 코드가 적힌 행은 현재 적용되지 않는다.</b> 맥락 없이 적용하면 그
     * 테마 밖에서까지 판단이 새어 나가므로 닫는 쪽으로 둔다. 테마별 대체지 경로가
     * 생기면 그때 살아난다.
     */
    @Column(name = "theme_code", length = 40)
    private String themeCode;

    /** 이 판단을 내린 이유. 근거 없는 큐레이션이 쌓이면 나중에 지울 수도 고칠 수도 없다. */
    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    @Builder
    private AlternativeCuration(String baseContentId, String targetNormalizedName,
                                String targetLawdCode, CurationAction action,
                                String themeCode, String reason) {

        this.baseContentId = baseContentId;
        this.targetNormalizedName = targetNormalizedName;
        this.targetLawdCode = targetLawdCode;
        this.action = action;
        this.themeCode = themeCode;
        this.reason = reason;
    }
}
