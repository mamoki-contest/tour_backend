package com.mamoki.tour.domain.search.entity;

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
 * 지원 테마의 동의어. 테이블 {@code supported_theme_synonym}.
 *
 * <p>{@code normalizedForm} 에 유니크 제약을 둔다. 같은 말이 두 테마를 가리키면 어느
 * 테마로 이어질지가 적재 순서에 달리게 되고, 그것은 조용히 어긋나는 종류의 오류다.
 * 시드 단계에서 막는다.
 *
 * <p>테마 코드는 외래키로 잇지 않고 코드 문자열로 둔다. 시드가 두 테이블을 각각
 * {@code ON DUPLICATE KEY UPDATE} 로 덮어쓰기 때문에, 식별자에 기대지 않는 편이 낫다.
 * 정의에 없는 코드를 가리키는 동의어는 적재 시 무시된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "supported_theme_synonym",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_supported_theme_synonym_normalized", columnNames = "normalized_form"),
        indexes = @Index(name = "idx_supported_theme_synonym_code", columnList = "theme_code")
)
public class ThemeSynonym extends BaseEntity {

    /** 가리키는 테마의 코드. {@code supported_theme.code} 와 같은 값이다. */
    @Column(name = "theme_code", nullable = false, length = 40)
    private String themeCode;

    /** 사용자가 입력할 법한 표기 그대로. */
    @Column(name = "synonym", nullable = false, length = 50)
    private String synonym;

    /**
     * 공백을 지우고 소문자로 내린 형태.
     *
     * <p>검색어 매칭은 적재 시점의 이 값이 아니라 읽을 때 {@code synonym} 을 다시
     * 정규화해서 한다. 이 컬럼은 "한 말이 한 테마만 가리킨다" 를 DB 제약으로 세우기
     * 위한 것이며, 시드 검증 테스트가 두 값이 어긋나지 않는지 확인한다.
     */
    @Column(name = "normalized_form", nullable = false, length = 50)
    private String normalizedForm;

    @Builder
    private ThemeSynonym(String themeCode, String synonym, String normalizedForm) {
        this.themeCode = themeCode;
        this.synonym = synonym;
        this.normalizedForm = normalizedForm;
    }
}
