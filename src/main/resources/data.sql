-- 강원특별자치도 18개 시·군 지역코드 매핑 시드.
--
-- lawd_code : 법정동 시·군 코드 5자리 (강원특별자치도 51)
-- area_code : 한국관광공사 영역 코드 (강원 32)
-- sigungu_code : 한국관광공사 시·군구 코드.
--   KorService2 areaCode2 로 조회하고, areaBasedList2 표본 500건의
--   (sigungucode, lDongRegnCd + lDongSignguCd) 쌍으로 교차 검증했다.
--
-- 재기동 시 중복 적재되지 않도록 lawd_code 유니크 제약과 ON DUPLICATE KEY UPDATE 를 사용한다.
INSERT INTO region_code (lawd_code, area_code, sigungu_code, name, created_at, modified_at) VALUES
    ('51110', '32', '13', '춘천시', NOW(), NOW()),
    ('51130', '32', '9',  '원주시', NOW(), NOW()),
    ('51150', '32', '1',  '강릉시', NOW(), NOW()),
    ('51170', '32', '3',  '동해시', NOW(), NOW()),
    ('51190', '32', '14', '태백시', NOW(), NOW()),
    ('51210', '32', '5',  '속초시', NOW(), NOW()),
    ('51230', '32', '4',  '삼척시', NOW(), NOW()),
    ('51720', '32', '16', '홍천군', NOW(), NOW()),
    ('51730', '32', '18', '횡성군', NOW(), NOW()),
    ('51750', '32', '8',  '영월군', NOW(), NOW()),
    ('51760', '32', '15', '평창군', NOW(), NOW()),
    ('51770', '32', '11', '정선군', NOW(), NOW()),
    ('51780', '32', '12', '철원군', NOW(), NOW()),
    ('51790', '32', '17', '화천군', NOW(), NOW()),
    ('51800', '32', '6',  '양구군', NOW(), NOW()),
    ('51810', '32', '10', '인제군', NOW(), NOW()),
    ('51820', '32', '2',  '고성군', NOW(), NOW()),
    ('51830', '32', '7',  '양양군', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    area_code = VALUES(area_code),
    sigungu_code = VALUES(sigungu_code),
    name = VALUES(name),
    modified_at = NOW();
