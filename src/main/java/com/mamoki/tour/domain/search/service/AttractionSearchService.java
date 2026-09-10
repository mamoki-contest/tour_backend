package com.mamoki.tour.domain.search.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.attraction.service.AttractionService;
import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.search.dto.AttractionSearchResponse;
import com.mamoki.tour.domain.search.dto.ThemeView;
import com.mamoki.tour.domain.search.enums.SearchResultType;
import com.mamoki.tour.domain.search.enums.SupportedTheme;
import com.mamoki.tour.domain.search.support.ThemeResolver;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.korservice.KorServiceClient;
import com.mamoki.tour.infra.korservice.KorServiceItemConverter;
import com.mamoki.tour.infra.korservice.dto.KorServiceResponse;

/**
 * 지원 테마 추천과 일반 검색.
 *
 * <p>검색어를 지원 테마로 이을 수 있으면 추천 자격을 적용한 결과를, 그렇지 않으면 공급자
 * 키워드 검색 결과를 그대로 돌려준다. 두 결과를 같은 유형으로 섞지 않는다.
 *
 * <p>지원 테마 결과에는 이름이 테마와 실제로 맞는 장소만 담는다. 자격은 공급자 검색 결과
 * 뒤에 오는 마지막 관문이라, 어떤 큐레이션도 이를 건너뛰고 후보를 밀어 넣을 수 없다.
 *
 * <p>결과가 없으면 억지 후보를 만들지 않고 빈 목록으로 두되, 가까운 지원 테마가 있으면
 * 제안으로 덧붙인다. 제안은 결과를 대신하는 값이 아니다.
 */
@Service
public class AttractionSearchService {

    /** 한국관광공사 영역 코드. 강원. */
    private static final String GANGWON_AREA_CODE = "32";

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String OPERATION = "searchKeyword2";

    /** 테마 하나에 검색어가 여럿이라 한 번에 넉넉히 받아 합친다. */
    private static final int FETCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(AttractionSearchService.class);

    private final KorServiceClient korServiceClient;
    private final ExternalApiCacheService cacheService;
    private final AttractionService attractionService;

    public AttractionSearchService(KorServiceClient korServiceClient,
                                   ExternalApiCacheService cacheService,
                                   AttractionService attractionService) {
        this.korServiceClient = korServiceClient;
        this.cacheService = cacheService;
        this.attractionService = attractionService;
    }

    public AttractionSearchResponse search(String query, String sigunguCode, int page, int size) {
        Optional<SupportedTheme> theme = ThemeResolver.resolve(query);

        return theme.map(matched -> searchTheme(matched, query, sigunguCode, page, size))
                .orElseGet(() -> searchGeneral(query, sigunguCode, page, size));
    }

    /** 지원 테마. 테마의 검색어를 모두 조회해 합친 뒤 자격을 적용한다. */
    private AttractionSearchResponse searchTheme(SupportedTheme theme, String query,
                                                 String sigunguCode, int page, int size) {

        Map<String, AttractionSnapshot> merged = new LinkedHashMap<>();
        DataStatus status = null;
        LocalDateTime collectedAt = null;

        for (String keyword : theme.keywords()) {
            Fetched fetched = fetch(keyword, sigunguCode);

            for (AttractionSnapshot snapshot : fetched.snapshots()) {
                // 검색어가 여럿이라 같은 장소가 겹쳐 온다. 먼저 나온 것을 남긴다.
                merged.putIfAbsent(snapshot.contentId(), snapshot);
            }

            if (fetched.status() != DataStatus.NO_DATA) {
                if (collectedAt == null) {
                    collectedAt = fetched.collectedAt();
                }
                if (status == null || fetched.status() == DataStatus.STALE) {
                    status = fetched.status();
                }
            }
        }

        List<AttractionSnapshot> qualified = merged.values().stream()
                .filter(snapshot -> isQualified(theme, snapshot))
                .toList();

        return respond(SearchResultType.SUPPORTED_THEME, ThemeView.of(theme),
                String.join(", ", theme.keywords()), qualified, query, theme,
                status == null ? DataStatus.NO_DATA : status, collectedAt, page, size);
    }

    /** 일반 검색. 공급자 결과를 그대로 쓰고 추천 자격을 적용하지 않는다. */
    private AttractionSearchResponse searchGeneral(String query, String sigunguCode,
                                                   int page, int size) {

        String normalized = ThemeResolver.normalize(query);

        if (normalized == null) {
            return respond(SearchResultType.GENERAL_SEARCH, null, null, List.of(), query, null,
                    DataStatus.AVAILABLE, null, page, size);
        }

        Fetched fetched = fetch(normalized, sigunguCode);

        return respond(SearchResultType.GENERAL_SEARCH, null, normalized,
                fetched.snapshots(), query, null, fetched.status(), fetched.collectedAt(), page, size);
    }

    /**
     * 추천 자격. 장소 이름에 테마를 가리키는 말이 실제로 들어가야 한다.
     *
     * <p>공급자 키워드 검색은 주소나 개요가 걸려도 결과에 넣어 준다. 그대로 두면 테마와
     * 상관없는 장소가 검증된 추천으로 올라간다.
     */
    private static boolean isQualified(SupportedTheme theme, AttractionSnapshot snapshot) {
        String name = ThemeResolver.normalize(snapshot.name());

        if (name == null) {
            return false;
        }

        return theme.matchTokens().stream()
                .map(ThemeResolver::normalize)
                .anyMatch(name::contains);
    }

    private AttractionSearchResponse respond(SearchResultType resultType, ThemeView appliedTheme,
                                             String appliedQuery, List<AttractionSnapshot> snapshots,
                                             String query, SupportedTheme theme, DataStatus status,
                                             LocalDateTime collectedAt, int page, int size) {

        List<AttractionResponse> described = snapshots.isEmpty()
                ? List.of()
                : attractionService.describe(snapshots, null, null, null);

        List<AttractionResponse> paged = pageOf(described, page, size);

        return new AttractionSearchResponse(resultType, appliedTheme, appliedQuery, paged,
                described.size(), page, size, suggestions(described, query, theme),
                status, status == DataStatus.NO_DATA ? null : collectedAt,
                KorServiceItemConverter.SOURCE);
    }

    /**
     * 결과가 비었을 때만 가까운 지원 테마를 제안한다.
     *
     * <p>보여 줄 것이 있는데 다른 테마를 권하면 결과가 부실하다는 신호로 읽힌다.
     * 이미 적용한 테마는 다시 권하지 않고, 가까운 테마가 없으면 빈 목록으로 둔다.
     */
    private static List<ThemeView> suggestions(List<AttractionResponse> described,
                                               String query, SupportedTheme applied) {
        if (!described.isEmpty()) {
            return List.of();
        }

        return ThemeResolver.suggest(query).stream()
                .filter(candidate -> candidate != applied)
                .map(ThemeView::of)
                .toList();
    }

    private static List<AttractionResponse> pageOf(List<AttractionResponse> items, int page, int size) {
        int from = Math.min((page - 1) * size, items.size());
        int to = Math.min(from + size, items.size());

        return items.subList(from, to);
    }

    private Fetched fetch(String keyword, String sigunguCode) {
        String requestKey = korServiceClient.searchKeywordKey(
                GANGWON_AREA_CODE, sigunguCode, null, keyword, 1, FETCH_SIZE);

        CachedResponse cached = cacheService.fetch(
                ApiProvider.KOR_SERVICE2,
                requestKey,
                () -> korServiceClient.searchKeywordJson(
                        GANGWON_AREA_CODE, sigunguCode, null, keyword, 1, FETCH_SIZE),
                CACHE_TTL);

        if (!cached.hasBody()) {
            return new Fetched(List.of(), DataStatus.NO_DATA, null);
        }

        KorServiceResponse parsed;
        try {
            parsed = korServiceClient.parse(OPERATION, cached.body());
        } catch (ExternalApiException e) {
            log.warn("캐시된 KorService2 검색 응답을 해석하지 못했습니다. requestKey={}", requestKey, e);
            return new Fetched(List.of(), DataStatus.NO_DATA, null);
        }

        // 여기서 빈 목록은 0건이다. 공급자를 못 부른 것과 구분해 상태를 그대로 전한다.
        return new Fetched(new ArrayList<>(KorServiceItemConverter.convertAll(parsed.items())),
                cached.status(), cached.collectedAt());
    }

    private record Fetched(List<AttractionSnapshot> snapshots, DataStatus status,
                           LocalDateTime collectedAt) {
    }
}
