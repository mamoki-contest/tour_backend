package com.mamoki.tour.domain.search.dto;

import com.mamoki.tour.domain.search.enums.SupportedTheme;
import com.mamoki.tour.domain.search.support.ThemeEntry;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지원 테마 표시용. 코드와 이름을 함께 내려 화면이 문구를 다시 만들지 않게 한다.
 *
 * <p>코드는 {@code SupportedTheme} 상수, 이름은 {@code supported_theme} 시드의 표시명이다.
 */
@Schema(description = "지원 테마")
public record ThemeView(

        @Schema(description = "테마 코드", example = "BEACH")
        SupportedTheme code,

        @Schema(description = "테마 이름", example = "해수욕장")
        String name
) {

    public static ThemeView of(ThemeEntry theme) {
        return new ThemeView(theme.code(), theme.displayName());
    }
}
