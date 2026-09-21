package com.mamoki.tour.domain.placeimage.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mamoki.tour.domain.placeimage.entity.PlaceImage;

public interface PlaceImageRepository extends JpaRepository<PlaceImage, Long> {

    Optional<PlaceImage> findByContentId(String contentId);

    /** 배치가 "이미 물어본 관광지" 를 가리는 데 쓴다. 상태와 무관하게 행이 있으면 물어본 것이다. */
    List<PlaceImage> findAllByContentIdIn(Collection<String> contentIds);

    /**
     * 조회에 실제로 쓸 수 있는 행만 읽는다.
     *
     * <p>{@code NONE}·{@code FAILED} 행을 함께 읽어 호출하는 쪽에서 거르게 두면, 어디 한
     * 곳에서 거르는 것을 잊는 순간 주소가 null 인 사진이 응답에 실린다. 질의에서 잘라 낸다.
     */
    @Query("""
            select p from PlaceImage p
            where p.contentId in :contentIds
              and p.status = com.mamoki.tour.domain.placeimage.enums.PlaceImageStatus.AVAILABLE
              and (p.imageUrl is not null or p.thumbnailUrl is not null)
            """)
    List<PlaceImage> findUsableByContentIdIn(@Param("contentIds") Collection<String> contentIds);
}
