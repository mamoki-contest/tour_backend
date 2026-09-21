package com.mamoki.tour.domain.placeimage.importer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mamoki.tour.domain.placeimage.entity.PlaceImage;
import com.mamoki.tour.domain.placeimage.repository.PlaceImageRepository;

/**
 * 수집 결과 하나를 표에 남긴다.
 *
 * <p>관광지 하나마다 트랜잭션을 나눈다. 한도 초과나 인증 실패로 중간에 멈춰도 그때까지
 * 채운 사진은 남아야 한다. 한 트랜잭션으로 묶으면 마지막 호출이 실패한 순간 수천 건이
 * 통째로 사라지고, 다음 실행이 같은 관광지를 처음부터 다시 부른다.
 */
@Service
public class PlaceImageWriter {

    private final PlaceImageRepository placeImageRepository;

    public PlaceImageWriter(PlaceImageRepository placeImageRepository) {
        this.placeImageRepository = placeImageRepository;
    }

    /** 같은 관광지의 행이 있으면 갈아 끼우고 없으면 새로 만든다({@code --refresh} 경로). */
    @Transactional
    public void record(PlaceImage collected) {
        placeImageRepository.findByContentId(collected.getContentId())
                .ifPresentOrElse(existing -> existing.replaceWith(collected),
                        () -> placeImageRepository.save(collected));
    }
}
