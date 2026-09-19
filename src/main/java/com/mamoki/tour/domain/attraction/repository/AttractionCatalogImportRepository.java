package com.mamoki.tour.domain.attraction.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.mamoki.tour.domain.attraction.entity.AttractionCatalogImport;

public interface AttractionCatalogImportRepository
        extends JpaRepository<AttractionCatalogImport, Long> {

    /**
     * 마지막으로 적재를 마친 시각. 한 번도 적재하지 않았으면 null.
     *
     * <p>목록의 정렬·경계 조회가 {@code collectedAt} 으로 쓴다. 실패한 적재는 트랜잭션과
     * 함께 사라져 이 표에 남지 않으므로, 이 값은 곧 <b>마지막 성공 적재 시각</b>이다(#69).
     *
     * <p>이력이 없는 것과 카탈로그가 비어 있는 것은 다르다. 이 기능이 생기기 전에 적재한
     * 환경에는 카탈로그가 가득한데 이력이 한 줄도 없다. 그래서 공급자 폴백 여부는 여전히
     * {@link AttractionRepository#findLatestCatalogChangeAt()} 로 판단한다.
     */
    @Query("select max(i.completedAt) from AttractionCatalogImport i")
    LocalDateTime findLatestCompletedAt();
}
