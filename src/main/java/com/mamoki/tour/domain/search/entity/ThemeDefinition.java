package com.mamoki.tour.domain.search.entity;

import com.mamoki.tour.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지원 테마 하나의 정의. 테이블 {@code supported_theme}.
 *
 * <p>운영자 화면이 PRD 범위 밖이라 {@code data.sql} 시드로 채운다(#54). 운영 판단으로
 * 바꾸는 값(표시명·검색어·자격 토큰·활성 여부)을 여기에 두고, 코드에는 테마 코드만 남긴다.
 *
 * <p>테이블이 비어 있으면 지원 테마가 하나도 없다. 그때는 모든 검색이 일반 검색으로
 * 떨어진다. 코드에 옛 정의를 남겨 두고 폴백하지 않는 이유는, 시드를 지웠는데도 지원
 * 테마 표시가 계속 붙으면 그 표시가 무엇을 근거로 하는지 알 수 없게 되기 때문이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "supported_theme",
        uniqueConstraints = @UniqueConstraint(name = "uk_supported_theme_code", columnNames = "code")
)
public class ThemeDefinition extends BaseEntity {

    /** 테마 코드. {@code SupportedTheme} 의 이름이며 응답 계약의 {@code code} 값이다. */
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    /** 사용자에게 보여줄 이름. */
    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    /**
     * 공급자에게 실제로 보낼 검색어. 쉼표로 구분한다.
     *
     * <p>테마 하나에 검색어가 두엇뿐이라 별도 테이블을 두지 않았다. 목록 전체를 한꺼번에
     * 읽어 쓰기만 하고 검색어 단건으로 조회할 일이 없다.
     */
    @Column(name = "search_keywords", nullable = false, length = 200)
    private String searchKeywords;

    /**
     * 추천 자격에 쓰는 말. 쉼표로 구분한다. 장소 이름에 이 중 하나가 들어가야 테마 결과에 담는다.
     *
     * <p>비어 있으면 어떤 장소도 자격을 얻지 못한다. 넓게 여는 쪽이 아니라 닫는 쪽으로
     * 틀린다. 자격은 결과에 붙는 보증이라, 값이 빠졌을 때 전부 통과시키면 보증이 사라진다.
     */
    @Column(name = "match_tokens", nullable = false, length = 200)
    private String matchTokens;

    /** 비활성 테마는 검색어로 이어지지도, 제안으로 나오지도 않는다. */
    @Column(name = "active", nullable = false)
    private boolean active;

    /** 표시와 제안의 정렬 순서. 같은 거리의 제안끼리 순서를 가른다. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private ThemeDefinition(String code, String displayName, String searchKeywords,
                            String matchTokens, boolean active, int sortOrder) {

        this.code = code;
        this.displayName = displayName;
        this.searchKeywords = searchKeywords;
        this.matchTokens = matchTokens;
        this.active = active;
        this.sortOrder = sortOrder;
    }
}
