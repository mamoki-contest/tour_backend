package com.mamoki.tour.domain.placeimage.entity;

import java.time.LocalDateTime;

import com.mamoki.tour.domain.placeimage.enums.PlaceImageStatus;
import com.mamoki.tour.global.entity.BaseEntity;
import com.mamoki.tour.global.enums.ImageSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공급자 사진이 없는 관광지 한 곳에 찾아 둔 대표 사진(#99).
 *
 * <p>카탈로그({@code attraction.image_url})를 고쳐 쓰지 않고 표를 따로 둔다. 카탈로그는
 * 공급자가 준 것을 그대로 담는 자리라, 우리가 다른 데서 찾아온 값을 그 칸에 넣으면 다음
 * 재적재가 조용히 지우거나(공급자 값이 빈 문자열로 덮음) 반대로 우리 값이 공급자 값처럼
 * 보인다. 출처를 가릴 수 있어야 화면에 표기할 수 있다.
 *
 * <p>찾지 못한 것도 행으로 남긴다({@code NONE}). 행이 없다는 것은 <b>아직 묻지 않았다</b>는
 * 뜻이어야 다음 실행이 무엇을 부를지 알 수 있다.
 *
 * <p><b>저작권:</b> 여기 담기는 것은 제3자 저작물의 주소다. 공모전 제출용(비상업)이라는
 * 전제에서만 쓰며, 이용 허락을 받은 사진이 아니다(README 참고).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "place_image",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_place_image_content_id", columnNames = "content_id"),
        indexes = @Index(name = "idx_place_image_status", columnList = "status")
)
public class PlaceImage extends BaseEntity {

    /**
     * 주소 컬럼이 담는 길이.
     *
     * <p>카탈로그의 {@code image_url}(500)보다 넉넉하다. 네이버 썸네일 주소는 원본 주소를
     * 통째로 인코딩해 파라미터에 싣기 때문에 공급자 사진 주소보다 길다.
     */
    public static final int MAX_URL_LENGTH = 1000;

    /** 표준 관광지 식별자. 카탈로그의 contentId 와 같은 값이며 관광지당 한 행이다. */
    @Column(name = "content_id", nullable = false, length = 30)
    private String contentId;

    /** 목록 카드가 쓸 썸네일. 찾지 못했으면 null. */
    @Column(name = "thumbnail_url", length = MAX_URL_LENGTH)
    private String thumbnailUrl;

    /** 상세가 쓸 원본 이미지. 찾지 못했으면 null. */
    @Column(name = "image_url", length = MAX_URL_LENGTH)
    private String imageUrl;

    /**
     * 이 사진을 찾은 자리.
     *
     * <p><b>저작권자의 페이지가 아니다.</b> 이미지 검색 응답에는 그 이미지가 실린 글의
     * 주소가 없어서(#99 확인) 저작권자에게 닿는 링크를 만들 수 없다. 대신 같은 검색어의
     * 네이버 이미지 검색 결과 주소를 남긴다 — 사람이 "이 사진이 어디서 왔나" 를 되짚을 수
     * 있는 유일한 자리다.
     */
    @Column(name = "source_url", length = MAX_URL_LENGTH)
    private String sourceUrl;

    /** 어느 공급자에게 물었는지. 오늘은 네이버 이미지 검색 하나뿐이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private ImageSource provider;

    /** 공급자에게 물어본 시각. 우리 DB 저장 시각(createdAt)과 구분한다. */
    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlaceImageStatus status;

    private PlaceImage(String contentId, String thumbnailUrl, String imageUrl, String sourceUrl,
                       ImageSource provider, LocalDateTime fetchedAt, PlaceImageStatus status) {
        this.contentId = contentId;
        this.thumbnailUrl = thumbnailUrl;
        this.imageUrl = imageUrl;
        this.sourceUrl = sourceUrl;
        this.provider = provider;
        this.fetchedAt = fetchedAt;
        this.status = status;
    }

    /** 쓸 수 있는 사진을 찾은 행. */
    public static PlaceImage found(String contentId, String thumbnailUrl, String imageUrl,
                                   String sourceUrl, ImageSource provider,
                                   LocalDateTime fetchedAt) {
        return new PlaceImage(contentId, thumbnailUrl, imageUrl, sourceUrl, provider, fetchedAt,
                PlaceImageStatus.AVAILABLE);
    }

    /** 물었지만 결과가 없던 행. 0건이며 실패가 아니다. */
    public static PlaceImage none(String contentId, String sourceUrl, ImageSource provider,
                                  LocalDateTime fetchedAt) {
        return new PlaceImage(contentId, null, null, sourceUrl, provider, fetchedAt,
                PlaceImageStatus.NONE);
    }

    /** 이 관광지 하나의 호출이 실패한 행. */
    public static PlaceImage failed(String contentId, String sourceUrl, ImageSource provider,
                                    LocalDateTime fetchedAt) {
        return new PlaceImage(contentId, null, null, sourceUrl, provider, fetchedAt,
                PlaceImageStatus.FAILED);
    }

    /**
     * 다시 수집한 결과로 갈아 끼운다({@code --refresh}).
     *
     * <p>행을 지우고 새로 만들지 않는 것은 {@code createdAt} 을 남겨 두기 위해서다.
     * 언제부터 이 관광지에 우리가 찾은 사진이 걸려 있었는지가 그 값에 남는다.
     */
    public void replaceWith(PlaceImage collected) {
        this.thumbnailUrl = collected.thumbnailUrl;
        this.imageUrl = collected.imageUrl;
        this.sourceUrl = collected.sourceUrl;
        this.provider = collected.provider;
        this.fetchedAt = collected.fetchedAt;
        this.status = collected.status;
    }

    /** 조회 응답이 쓸 수 있는 행인가. 상태만이 아니라 주소가 실제로 있는지도 본다. */
    public boolean isUsable() {
        return status == PlaceImageStatus.AVAILABLE && (imageUrl != null || thumbnailUrl != null);
    }

    /** 컬럼에 담기지 않는 주소는 null 로 본다. 잘라서 넣으면 열리지 않는 주소가 된다. */
    public static String fitOrNull(String url) {
        return url == null || url.length() > MAX_URL_LENGTH ? null : url;
    }
}
