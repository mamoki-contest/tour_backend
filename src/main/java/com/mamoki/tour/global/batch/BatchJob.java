package com.mamoki.tour.global.batch;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 커맨드라인으로 돌릴 수 있는 작업.
 *
 * <p>이름은 사람이 직접 입력하는 값이라 코드명과 따로 둔다. 상수 이름을 바꿔도 운영자가
 * 쓰던 명령이 깨지지 않게 하려는 것이다.
 */
public enum BatchJob {

    /** 관광지 카탈로그를 공급자에서 받아 적재한다. 다른 작업들의 바탕이다. */
    CATALOG("catalog", "관광지 카탈로그 적재"),

    /** 카탈로그를 순회해 온라인 언급량을 수집한다. 카탈로그가 먼저 채워져 있어야 한다. */
    MENTION("mention", "온라인 언급량 월간 수집"),

    /** 내려받은 TMAP 검색순위 zip 묶음을 적재한다. */
    TMAP("tmap", "TMAP 검색순위 적재"),

    /** 내려받은 주요관광지점 입장객통계 엑셀을 적재한다. */
    VISITOR_STATS("visitor-stats", "주요관광지점 입장객통계 적재");

    private final String jobName;
    private final String description;

    BatchJob(String jobName, String description) {
        this.jobName = jobName;
        this.description = description;
    }

    public String jobName() {
        return jobName;
    }

    public String description() {
        return description;
    }

    public static Optional<BatchJob> from(String jobName) {
        return Arrays.stream(values())
                .filter(job -> job.jobName.equalsIgnoreCase(jobName))
                .findFirst();
    }

    public static String names() {
        return Arrays.stream(values())
                .map(job -> "%s(%s)".formatted(job.jobName, job.description))
                .collect(Collectors.joining(", "));
    }
}
