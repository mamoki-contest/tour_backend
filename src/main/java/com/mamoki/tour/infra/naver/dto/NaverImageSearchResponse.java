package com.mamoki.tour.infra.naver.dto;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 네이버 이미지 검색 응답.
 *
 * <p>우리가 쓰는 것은 첫 유효 항목 하나다. 사진이 없는 관광지의 자리를 메우는 것이 목적이라
 * 여러 장을 보여 줄 데가 없고, 관련도 정렬({@code sort=sim})의 1위가 그 자리에 놓을 한 장이다.
 *
 * <p><b>출처 페이지 주소는 이 응답에 없다.</b> 항목이 주는 것은 이미지 파일 주소({@code link})와
 * 썸네일 주소({@code thumbnail}) 뿐이고, 그 이미지가 실린 글의 주소는 공급자가 주지 않는다.
 * 그래서 저작권자에게 닿는 링크를 이 응답만으로는 만들 수 없다 — 출처 표기의 한계다.
 *
 * @param total 검색 결과 수. 우리는 쓰지 않으며 진단 용도로만 둔다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverImageSearchResponse(
        String lastBuildDate,
        Long total,
        Integer start,
        Integer display,
        List<Item> items
) {

    /**
     * 이미지 한 장.
     *
     * @param link       원본 이미지 주소. 상세 화면이 쓴다.
     * @param thumbnail  썸네일 주소. 목록 카드가 쓴다.
     * @param sizeheight 세로 픽셀. 공급자가 문자열로 준다.
     * @param sizewidth  가로 픽셀. 공급자가 문자열로 준다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String title, String link, String thumbnail,
                       String sizeheight, String sizewidth) {

        /** 둘 중 하나라도 있으면 쓸 수 있다. 둘 다 없는 항목은 자리만 차지한다. */
        public boolean hasImage() {
            return isPresent(link) || isPresent(thumbnail);
        }

        /** 상세가 쓸 원본 주소. 원본이 비면 썸네일이라도 쓴다 — 사진이 없는 것보다 낫다. */
        public String imageUrl() {
            return isPresent(link) ? link.strip() : thumbnailOrNull();
        }

        /** 카드가 쓸 썸네일 주소. 썸네일이 비면 원본을 쓴다(이슈 #99 의 규칙). */
        public String thumbnailUrl() {
            return isPresent(thumbnail) ? thumbnail.strip() : linkOrNull();
        }

        private String linkOrNull() {
            return isPresent(link) ? link.strip() : null;
        }

        private String thumbnailOrNull() {
            return isPresent(thumbnail) ? thumbnail.strip() : null;
        }

        private static boolean isPresent(String value) {
            return value != null && !value.isBlank();
        }
    }

    /**
     * 쓸 수 있는 첫 항목.
     *
     * <p>결과가 없으면 비어 있는 결과를 돌려준다. 여기서 값을 만들어내지 않는다 — 0건과
     * "아직 찾지 못함" 은 다른 상태이고, 그 구분은 부른 쪽이 표에 남긴다.
     */
    public Optional<Item> firstUsable() {
        if (items == null) {
            return Optional.empty();
        }

        return items.stream().filter(Item::hasImage).findFirst();
    }
}
