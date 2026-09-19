package com.mamoki.tour.domain.placemapping.importer;

/**
 * 한 원천의 매칭률. 분모를 정리하기 전과 후를 함께 들고 다닌다.
 *
 * <p><b>둘을 함께 들고 다니는 것이 이 타입의 전부다.</b> 정리한 뒤 수치만 남기면 그것이
 * 실제로 더 이어서 오른 것인지 분모를 줄여서 오른 것인지 뒤에서 구별할 수 없다. 로그도
 * README 도 PR 도 항상 두 값을 나란히 적는다.
 *
 * @param totalRows        원천의 전체 행
 * @param matchedRows      카탈로그에 이어진 행. 이번 실행이 확정한 매핑까지 반영한 값이다.
 * @param outOfCatalogRows 카탈로그 대상이 아니라고 판정해 분모에서 뺀 행
 */
public record SourceMatchRate(int totalRows, int matchedRows, int outOfCatalogRows) {

    public static SourceMatchRate none() {
        return new SourceMatchRate(0, 0, 0);
    }

    /** 분모를 정리하기 전의 매칭률. 원천 파일이 실제로 얼마나 쓰이는지다. */
    public double beforeExclusion() {
        return ratio(matchedRows, totalRows);
    }

    /** 카탈로그 대상이 아닌 행을 뺀 매칭률. 이을 수 있는 것 중 얼마나 이었는지다. */
    public double afterExclusion() {
        return ratio(matchedRows, denominatorAfterExclusion());
    }

    public int denominatorAfterExclusion() {
        return Math.max(0, totalRows - outOfCatalogRows);
    }

    /** 한 줄 요약. 제외 후만 적어 좋아 보이게 하지 않도록 두 값을 함께 만든다. */
    public String summary() {
        if (totalRows == 0) {
            return "분모 없음";
        }

        return "매칭률 제외 전 %d/%d (%.1f%%), 제외 후 %d/%d (%.1f%%), 분모에서 뺀 행=%d"
                .formatted(matchedRows, totalRows, beforeExclusion() * 100,
                        matchedRows, denominatorAfterExclusion(), afterExclusion() * 100,
                        outOfCatalogRows);
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }
}
