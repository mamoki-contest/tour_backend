package com.mamoki.tour.domain.attraction.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mamoki.tour.domain.attraction.dto.AttractionListResponse;
import com.mamoki.tour.domain.attraction.dto.AttractionSearchRequest;
import com.mamoki.tour.domain.attraction.service.AttractionService;
import com.mamoki.tour.global.rsdata.ResultCodes;
import com.mamoki.tour.global.rsdata.RsData;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/attractions")
public class AttractionController {

    private final AttractionService attractionService;

    public AttractionController(AttractionService attractionService) {
        this.attractionService = attractionService;
    }

    /**
     * 탐색 홈의 관광지 목록을 조회한다.
     *
     * <p>데이터를 얻지 못해도 200 으로 응답하며, 그 사실은 dataStatus 로 알린다.
     * 공급자 장애는 오류가 아니라 정상 응답의 한 상태다.
     */
    @GetMapping
    public RsData<AttractionListResponse> getAttractions(
            @Valid @ModelAttribute AttractionSearchRequest request) {

        return RsData.of(ResultCodes.OK, "관광지 목록을 조회했습니다.", attractionService.search(request));
    }
}
