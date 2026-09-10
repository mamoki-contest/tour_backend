package com.mamoki.tour.domain.collection.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.attraction.dto.AttractionResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.attraction.service.AttractionDetailService;
import com.mamoki.tour.domain.attraction.service.AttractionDetailService.BasicLookup;
import com.mamoki.tour.domain.attraction.service.AttractionService;
import com.mamoki.tour.domain.collection.dto.CollectionItemView;
import com.mamoki.tour.domain.collection.dto.CollectionLookupResponse;
import com.mamoki.tour.domain.collection.enums.CollectionItemStatus;
import com.mamoki.tour.global.exception.ServiceException;
import com.mamoki.tour.global.rsdata.ResultCodes;
import com.mamoki.tour.infra.korservice.KorServiceItemConverter;

/**
 * 개인 컬렉션에 저장된 식별자로 최신 표시정보를 다시 조회한다.
 *
 * <p>컬렉션 자체는 프론트 브라우저 저장소가 관리한다. 여기에는 사용자별 저장소도, 계정도,
 * 동기화도 두지 않는다. 저장된 식별자를 받아 지금 상태를 되돌려주는 것이 전부다.
 *
 * <p>한 건이 실패해도 나머지를 정상으로 돌려준다. 컬렉션 화면은 저장해 둔 장소를 보여주는
 * 곳이라, 그중 하나가 없어졌다는 이유로 화면 전체가 오류가 되면 나머지도 볼 수 없다.
 */
@Service
public class CollectionLookupService {

    /**
     * 한 번에 조회할 수 있는 식별자 수.
     *
     * <p>식별자마다 공급자를 한 번씩 부른다. 캐시가 비어 있으면 이 수만큼 순차 호출이
     * 일어나므로 상한을 둔다. 컬렉션이 더 크면 프론트가 나눠 부른다.
     */
    public static final int MAX_CONTENT_IDS = 50;

    private final AttractionDetailService attractionDetailService;
    private final AttractionService attractionService;

    public CollectionLookupService(AttractionDetailService attractionDetailService,
                                   AttractionService attractionService) {
        this.attractionDetailService = attractionDetailService;
        this.attractionService = attractionService;
    }

    public CollectionLookupResponse lookup(List<String> contentIds) {
        List<String> requested = normalize(contentIds);

        if (requested.isEmpty()) {
            throw new ServiceException(ResultCodes.INVALID_REQUEST, "조회할 식별자가 없습니다.");
        }

        if (requested.size() > MAX_CONTENT_IDS) {
            throw new ServiceException(ResultCodes.INVALID_REQUEST,
                    "한 번에 조회할 수 있는 식별자는 %d개까지입니다.".formatted(MAX_CONTENT_IDS));
        }

        Map<String, BasicLookup> lookups = new LinkedHashMap<>();
        List<AttractionSnapshot> found = new ArrayList<>();

        for (String contentId : requested) {
            BasicLookup lookup = attractionDetailService.findBasic(contentId);
            lookups.put(contentId, lookup);

            if (lookup.detail() != null) {
                found.add(lookup.detail().basic());
            }
        }

        // 표시정보 조립은 목록·검색과 같은 경로를 쓴다. 따로 만들면 같은 장소가 화면마다 달라진다.
        Map<String, AttractionResponse> described = new LinkedHashMap<>();
        for (AttractionResponse response : attractionService.describe(found, null, null, null)) {
            described.put(response.contentId(), response);
        }

        List<CollectionItemView> items = new ArrayList<>(requested.size());
        int available = 0;
        int notFound = 0;
        int unavailable = 0;

        for (String contentId : requested) {
            BasicLookup lookup = lookups.get(contentId);

            if (lookup.isUnreachable()) {
                items.add(CollectionItemView.unavailable(contentId));
                unavailable++;
                continue;
            }

            AttractionResponse response = lookup.detail() == null
                    ? null
                    : described.get(lookup.detail().basic().contentId());

            if (response == null) {
                items.add(CollectionItemView.notFound(contentId));
                notFound++;
                continue;
            }

            items.add(new CollectionItemView(contentId, CollectionItemStatus.AVAILABLE,
                    response, lookup.cached().collectedAt()));
            available++;
        }

        return new CollectionLookupResponse(items, requested.size(), available, notFound,
                unavailable, KorServiceItemConverter.SOURCE);
    }

    /** 빈 값을 버리고 중복을 지운다. 같은 장소를 두 번 저장했다고 공급자를 두 번 부르지 않는다. */
    private static List<String> normalize(List<String> contentIds) {
        if (contentIds == null) {
            return List.of();
        }

        return new ArrayList<>(contentIds.stream()
                .filter(contentId -> contentId != null && !contentId.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }
}
