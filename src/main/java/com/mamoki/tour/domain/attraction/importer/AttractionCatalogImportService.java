package com.mamoki.tour.domain.attraction.importer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.attraction.dto.AttractionSnapshot;
import com.mamoki.tour.domain.attraction.entity.Attraction;
import com.mamoki.tour.domain.attraction.repository.AttractionRepository;
import com.mamoki.tour.domain.region.entity.RegionCode;
import com.mamoki.tour.domain.region.repository.RegionCodeRepository;
import com.mamoki.tour.global.enums.DataStatus;
import com.mamoki.tour.infra.korservice.KorServiceClient;
import com.mamoki.tour.infra.korservice.KorServiceItemConverter;
import com.mamoki.tour.infra.korservice.dto.KorServiceResponse;

/**
 * 강원 관광지 카탈로그를 공급자에서 받아 적재한다.
 *
 * <p>카탈로그는 다른 신호들이 붙는 바탕이다. 온라인 언급량 수집이 이 테이블을 순회하고,
 * TMAP 순위와 입장객 통계가 여기 담긴 {@code contentId} 에 매칭된다. 비어 있으면 셋 다
 * 제 기능을 못 한다.
 *
 * <p>분류를 지정하지 않고 전체를 담는다. 목록 API 가 분류를 비우면 전체를 돌려주므로,
 * 음식점·숙박을 빼면 목록에 나오는 장소가 카탈로그에 없는 상태가 된다.
 *
 * <p>캐시 계층을 거치지 않고 공급자를 직접 부른다. 운영자가 돌리는 배치라 최신 값을 받아야
 * 하고, 24시간 캐시는 사용자 조회를 위한 것이다.
 */
@Service
public class AttractionCatalogImportService {

    /**
     * 법정동 시·도 코드. 강원.
     *
     * <p>한국관광공사 영역 코드(32)가 아니라 법정동 코드로 조회한다. 영역 코드로 거르면
     * 공급자 데이터에서 areacode 가 비어 있는 항목이 통째로 빠지기 때문이다(#44).
     */
    private static final String GANGWON_LAWD_REGION_CODE = "51";

    private static final String OPERATION = "areaBasedList2";

    /** 한 번에 받아오는 크기. 공급자 상한이 100 이다. */
    private static final int PAGE_SIZE = 100;

    /**
     * 최대 페이지 수. 강원 전체가 4,800곳 안팎이라 이 값이면 충분히 담는다.
     * 공급자가 갑자기 훨씬 많은 값을 돌려줄 때 호출이 끝없이 늘어나지 않도록 두는 상한이다.
     */
    private static final int MAX_PAGES = 50;

    private static final Logger log = LoggerFactory.getLogger(AttractionCatalogImportService.class);

    private final KorServiceClient korServiceClient;
    private final AttractionRepository attractionRepository;
    private final RegionCodeRepository regionCodeRepository;

    public AttractionCatalogImportService(KorServiceClient korServiceClient,
                                          AttractionRepository attractionRepository,
                                          RegionCodeRepository regionCodeRepository) {
        this.korServiceClient = korServiceClient;
        this.attractionRepository = attractionRepository;
        this.regionCodeRepository = regionCodeRepository;
    }

    @Transactional
    public AttractionCatalogImportResult importAll() {
        List<AttractionSnapshot> snapshots = fetchAll();

        if (snapshots.isEmpty()) {
            throw new IllegalStateException("공급자에서 받아온 관광지가 없어 카탈로그를 적재하지 않습니다.");
        }

        Map<String, RegionCode> regionsByLawdCode = new HashMap<>();
        for (RegionCode region : regionCodeRepository.findAll()) {
            regionsByLawdCode.put(region.getLawdCode(), region);
        }

        int inserted = 0;
        int updated = 0;
        int regionUnmapped = 0;

        for (AttractionSnapshot snapshot : snapshots) {
            RegionCode region = snapshot.lawdCode() == null
                    ? null : regionsByLawdCode.get(snapshot.lawdCode());

            if (region == null) {
                regionUnmapped++;
            }

            // 식별자가 같으면 같은 장소다. 새로 만들지 않고 값만 새로 쓴다.
            var existing = attractionRepository.findByContentId(snapshot.contentId());

            if (existing.isPresent()) {
                existing.get().refresh(snapshot.name(), snapshot.imageUrl(), snapshot.address(),
                        snapshot.latitude(), snapshot.longitude(), snapshot.contentTypeId(),
                        region, DataStatus.AVAILABLE, snapshot.baseAt());
                updated++;
                continue;
            }

            attractionRepository.save(Attraction.builder()
                    .contentId(snapshot.contentId())
                    .name(snapshot.name())
                    .imageUrl(snapshot.imageUrl())
                    .address(snapshot.address())
                    .latitude(snapshot.latitude())
                    .longitude(snapshot.longitude())
                    .contentTypeId(snapshot.contentTypeId())
                    .regionCode(region)
                    .dataStatus(DataStatus.AVAILABLE)
                    .baseAt(snapshot.baseAt())
                    .source(KorServiceItemConverter.SOURCE)
                    .build());
            inserted++;
        }

        log.info("관광지 카탈로그 적재 완료: 받음={}, 신규={}, 갱신={}, 지역 미매핑={}",
                snapshots.size(), inserted, updated, regionUnmapped);

        return new AttractionCatalogImportResult(snapshots.size(), inserted, updated, regionUnmapped);
    }

    /**
     * 공급자 페이지를 끝까지 받아 모은다.
     *
     * <p>같은 장소가 페이지 경계에서 겹쳐 오는 경우가 있어 식별자로 한 번 걸러낸다.
     * 공급자에서 사라진 장소는 지우지 않는다. 개인 컬렉션(#9)이 저장해 둔 식별자가 끊긴다.
     */
    private List<AttractionSnapshot> fetchAll() {
        Map<String, AttractionSnapshot> byContentId = new LinkedHashMap<>();
        int totalCount = Integer.MAX_VALUE;

        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            KorServiceResponse response = korServiceClient.parse(OPERATION,
                    korServiceClient.areaBasedListByLawdJson(
                            GANGWON_LAWD_REGION_CODE, null, null, pageNo, PAGE_SIZE));

            List<AttractionSnapshot> page = KorServiceItemConverter.convertAll(response.items());
            totalCount = response.totalCount();

            if (page.isEmpty()) {
                break;
            }

            for (AttractionSnapshot snapshot : page) {
                byContentId.putIfAbsent(snapshot.contentId(), snapshot);
            }

            if (pageNo * PAGE_SIZE >= totalCount) {
                break;
            }
        }

        if (byContentId.size() < totalCount && totalCount != Integer.MAX_VALUE) {
            log.warn("카탈로그를 끝까지 받지 못했습니다. 받은 곳={}, 공급자 전체={}, 상한 페이지={}",
                    byContentId.size(), totalCount, MAX_PAGES);
        }

        return new ArrayList<>(byContentId.values());
    }
}
