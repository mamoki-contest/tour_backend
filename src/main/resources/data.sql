-- 강원특별자치도 18개 시·군 지역코드 매핑 시드.
-- 재기동 시 중복 적재되지 않도록 INSERT IGNORE 와 lawd_code 유니크 제약을 사용한다.
-- sigungu_code 는 KorService2 areaCode2 조회로 확인한 뒤 채운다. 임의 값을 넣지 않는다.
INSERT IGNORE INTO region_code (lawd_code, area_code, sigungu_code, name, created_at, modified_at) VALUES
    ('51110', '32', NULL, '춘천시', NOW(), NOW()),
    ('51130', '32', NULL, '원주시', NOW(), NOW()),
    ('51150', '32', NULL, '강릉시', NOW(), NOW()),
    ('51170', '32', NULL, '동해시', NOW(), NOW()),
    ('51190', '32', NULL, '태백시', NOW(), NOW()),
    ('51210', '32', NULL, '속초시', NOW(), NOW()),
    ('51230', '32', NULL, '삼척시', NOW(), NOW()),
    ('51720', '32', NULL, '홍천군', NOW(), NOW()),
    ('51730', '32', NULL, '횡성군', NOW(), NOW()),
    ('51750', '32', NULL, '영월군', NOW(), NOW()),
    ('51760', '32', NULL, '평창군', NOW(), NOW()),
    ('51770', '32', NULL, '정선군', NOW(), NOW()),
    ('51780', '32', NULL, '철원군', NOW(), NOW()),
    ('51790', '32', NULL, '화천군', NOW(), NOW()),
    ('51800', '32', NULL, '양구군', NOW(), NOW()),
    ('51810', '32', NULL, '인제군', NOW(), NOW()),
    ('51820', '32', NULL, '고성군', NOW(), NOW()),
    ('51830', '32', NULL, '양양군', NOW(), NOW());
