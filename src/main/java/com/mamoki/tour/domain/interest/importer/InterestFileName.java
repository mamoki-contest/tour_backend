package com.mamoki.tour.domain.interest.importer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 데이터랩 다운로드 zip 파일명에서 지역과 원천 조회기간을 읽는다.
 *
 * <p>CSV 안에는 지역 컬럼이 없다. 어느 시·군의 데이터인지는 파일명에만 있으므로,
 * 파일명을 신뢰할 수 있는 형식으로 검증한 뒤 사용한다.
 *
 * <p>예: {@code 20260906200452_강원특별자치도+강릉시_202508-202607_데이터랩_다운로드.zip}
 *
 * @param sido        시·도명 (강원특별자치도)
 * @param sigungu     시·군명 (강릉시)
 * @param startYearMonth 원천 조회 시작월 (202508)
 * @param endYearMonth   원천 조회 종료월 (202607)
 */
public record InterestFileName(
        String downloadedAt,
        String sido,
        String sigungu,
        String startYearMonth,
        String endYearMonth
) {

    private static final Pattern PATTERN = Pattern.compile(
            "^(\\d{14})_([^+_]+)\\+([^+_]+)_(\\d{6})-(\\d{6})_.*\\.zip$");

    public static InterestFileName parse(String fileName) {
        Matcher matcher = PATTERN.matcher(fileName);

        if (!matcher.matches()) {
            throw new InterestImportException(
                    "데이터랩 다운로드 파일명 형식이 아닙니다: " + fileName);
        }

        return new InterestFileName(
                matcher.group(1),
                matcher.group(2),
                matcher.group(3),
                matcher.group(4),
                matcher.group(5));
    }

    /** 원천 조회기간 표기. 스냅샷에 그대로 기록한다. */
    public String sourcePeriod() {
        return startYearMonth + "-" + endYearMonth;
    }
}
