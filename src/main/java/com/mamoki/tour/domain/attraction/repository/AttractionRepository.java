package com.mamoki.tour.domain.attraction.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.mamoki.tour.domain.attraction.entity.Attraction;

public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    Optional<Attraction> findByContentId(String contentId);

    /**
     * 지역까지 함께 읽는다. 배치처럼 트랜잭션 밖에서 지역명을 쓰는 곳에서는
     * 지연 로딩이 터지므로 이 메서드를 쓴다.
     *
     * <p>순서를 못 박는다(#84). 매칭 색인이 같은 이름 중 먼저 담긴 쪽을 쓰는데, 순서를
     * 정하지 않으면 DB 가 돌려주는 순서에 따라 승자가 바뀐다. 재적재나 인덱스 변화만으로
     * 어제와 다른 관광지에 TMAP 순위가 붙는데, 값이 그럴듯해 아무도 알아채지 못한다.
     */
    @Query("select a from Attraction a left join fetch a.regionCode order by a.contentId")
    List<Attraction> findAllWithRegion();

    /**
     * 한 시·군의 카탈로그를 지역까지 함께 읽는다.
     *
     * <p>목록의 정렬·경계 조회(#51)가 쓴다. 지역명을 트랜잭션 밖에서 붙이므로 지연 로딩을
     * 쓸 수 없고, 지역 매핑이 없는 장소는 시·군을 지정한 조회의 대상이 아니라 내부 조인이다.
     */
    @Query("select a from Attraction a join fetch a.regionCode r where r.lawdCode = :lawdCode")
    List<Attraction> findAllWithRegionByLawdCode(String lawdCode);

    /**
     * 카탈로그 내용이 마지막으로 바뀐 시각. 한 건도 없으면 null.
     *
     * <p><b>적재를 실행한 시각이 아니다.</b> {@code modifiedAt} 은 행이 실제로 바뀔 때만
     * 움직이는 감사 필드라, 공급자 내용이 그대로인 재적재는 이 값을 밀지 않는다. 오늘
     * 재적재를 돌려도 내용이 같으면 지난달 시각이 그대로 나온다. 그것이 틀린 값은 아니다
     * — 사용자가 보는 데이터가 실제로 그때 것이기 때문이다. 다만 "언제 돌렸는지" 를
     * 묻는 데 쓰면 안 된다. 그 물음에 답하려면 적재 실행 이력을 따로 남겨야 한다.
     *
     * <p>카탈로그가 비어 있는지 판단하는 데도 같이 쓴다. `조건에 맞는 장소가 없음`과
     * `카탈로그를 아직 적재하지 않음`은 다르게 다뤄야 한다(#51).
     */
    @Query("select max(a.modifiedAt) from Attraction a")
    LocalDateTime findLatestCatalogChangeAt();

    /**
     * 카탈로그의 관광지명만 전부 읽는다.
     *
     * <p>미매칭 재분류(#72)가 "카탈로그가 이 종류를 실제로 담고 있는가" 를 확인하는 데 쓴다.
     * 엔티티를 통째로 읽으면 4천여 행을 영속성 컨텍스트에 올리는데, 여기서 필요한 것은
     * 이름뿐이다.
     */
    @Query("select a.name from Attraction a where a.name is not null")
    List<String> findAllNames();

    /** 개인 컬렉션 재조회(#9)가 사용하는 배치 조회. */
    List<Attraction> findAllByContentIdIn(Collection<String> contentIds);

    List<Attraction> findAllByRegionCodeLawdCode(String lawdCode);

    /** 지도 경계(bbox) 조회. 좌표가 없는 장소는 결과에서 제외된다. */
    List<Attraction> findAllByLatitudeBetweenAndLongitudeBetween(
            BigDecimal minLatitude, BigDecimal maxLatitude,
            BigDecimal minLongitude, BigDecimal maxLongitude);
}
