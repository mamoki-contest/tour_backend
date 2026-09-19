package com.mamoki.tour.domain.currentaccess.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mamoki.tour.domain.cache.dto.CachedResponse;
import com.mamoki.tour.domain.cache.service.ExternalApiCacheService;
import com.mamoki.tour.domain.currentaccess.dto.CurrentAccessView;
import com.mamoki.tour.domain.currentaccess.dto.RoadFlowView;
import com.mamoki.tour.global.enums.ApiProvider;
import com.mamoki.tour.global.exception.ExternalApiException;
import com.mamoki.tour.infra.its.ItsClient;
import com.mamoki.tour.infra.its.ItsItemConverter;

/**
 * 관광지까지 가는 길의 현재 여건을 모은다.
 *
 * <p>도로는 국가교통정보센터에서, 주차는 강릉시 교통정보 조회서비스와 전국주차장정보
 * 표준데이터에서 온다. 셋은 서로 다른 공급자이고 커버리지도 다르다. 하나가 없다고 나머지를
 * 감추지 않는다.
 *
 * <p>도로 캐시를 5분만 둔다. 다른 신호들의 24시간과 다르다. 지금 이 순간의 도로 상태라
 * 오래된 값은 쓸모가 없고, 공급자도 5분 단위로 갱신한다.
 *
 * <p>좌표가 없으면 조회하지 않는다. 어디 주변을 물어야 할지 알 수 없는데 임의의 좌표로
 * 물으면 다른 동네의 도로·주차 상태를 그 관광지의 것으로 보여주게 된다.
 */
@Service
public class CurrentAccessService {

    /** 공급자가 5분 단위로 갱신한다. 그보다 길게 잡으면 지난 관측을 현재로 보여주게 된다. */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(CurrentAccessService.class);

    private final ItsClient itsClient;
    private final ExternalApiCacheService cacheService;
    private final ParkingAccessService parkingAccessService;

    public CurrentAccessService(ItsClient itsClient, ExternalApiCacheService cacheService,
                                ParkingAccessService parkingAccessService) {
        this.itsClient = itsClient;
        this.cacheService = cacheService;
        this.parkingAccessService = parkingAccessService;
    }

    public CurrentAccessView resolve(BigDecimal latitude, BigDecimal longitude) {
        return new CurrentAccessView(
                roadFlow(latitude, longitude),
                parkingAccessService.resolve(latitude, longitude),
                LocalDateTime.now(),
                ItsItemConverter.SOURCE);
    }

    private RoadFlowView roadFlow(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return RoadFlowView.noData();
        }

        String requestKey = itsClient.trafficInfoKey(latitude, longitude);

        CachedResponse cached = cacheService.fetch(
                ApiProvider.ITS_TRAFFIC,
                requestKey,
                () -> itsClient.trafficInfoJson(latitude, longitude),
                CACHE_TTL);

        if (!cached.hasBody()) {
            return RoadFlowView.noData();
        }

        try {
            return ItsItemConverter.convert(itsClient.parse(cached.body()).items());
        } catch (ExternalApiException e) {
            log.warn("캐시된 교통소통 응답을 해석하지 못했습니다. requestKey={}", requestKey, e);
            return RoadFlowView.noData();
        }
    }
}
