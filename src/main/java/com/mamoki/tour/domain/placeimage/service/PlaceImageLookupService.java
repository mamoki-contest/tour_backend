package com.mamoki.tour.domain.placeimage.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.placeimage.dto.PlaceImageView;
import com.mamoki.tour.domain.placeimage.entity.PlaceImage;
import com.mamoki.tour.domain.placeimage.repository.PlaceImageRepository;

/**
 * 찾아 둔 대표 사진을 조회 쪽에 건넨다(#99).
 *
 * <p>쓸 수 있는 행만 돌려준다. 결과가 없던 행({@code NONE})과 실패한 행({@code FAILED})은
 * 배치가 "다시 묻지 않기 위해" 남긴 기록이지 화면에 걸 사진이 아니다.
 *
 * <p>목록은 한 번에 여러 식별자로 묻는다. 카드마다 한 번씩 물으면 지도를 밀 때마다
 * 수십 개의 질의가 나간다.
 */
@Service
public class PlaceImageLookupService {

    private final PlaceImageRepository placeImageRepository;

    public PlaceImageLookupService(PlaceImageRepository placeImageRepository) {
        this.placeImageRepository = placeImageRepository;
    }

    /**
     * @param contentIds 사진이 비어 있는 관광지의 식별자만 넘긴다. 공급자 사진이 있는 곳까지
     *                   물으면 쓰지도 않을 행을 읽는다.
     * @return 찾아 둔 사진이 있는 식별자만 담긴다. 없는 식별자는 키 자체가 없다.
     */
    @Transactional(readOnly = true)
    public Map<String, PlaceImageView> findUsable(Collection<String> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Map.of();
        }

        List<PlaceImage> rows = placeImageRepository.findUsableByContentIdIn(contentIds);
        Map<String, PlaceImageView> views = new LinkedHashMap<>();

        for (PlaceImage row : rows) {
            views.put(row.getContentId(), PlaceImageView.from(row));
        }

        return views;
    }

    /** 상세 한 건. 값이 없으면 비어 있는 결과다 — 사진을 만들어내지 않는다. */
    @Transactional(readOnly = true)
    public Optional<PlaceImageView> findUsable(String contentId) {
        if (contentId == null || contentId.isBlank()) {
            return Optional.empty();
        }

        return placeImageRepository.findByContentId(contentId)
                .filter(PlaceImage::isUsable)
                .map(PlaceImageView::from);
    }
}
