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

-- 지원 테마 시드 (#54).
--
-- 운영자 화면이 PRD 범위 밖이라 두 테이블 모두 이 파일로만 채운다.
-- code 는 SupportedTheme 의 상수 이름이며, 여기에만 있는 코드는 적재 시 무시된다.
-- search_keywords : 공급자(KorService2)에게 보낼 검색어. 쉼표 구분.
-- match_tokens    : 추천 자격. 장소 이름에 이 중 하나가 들어가야 테마 결과에 담는다. 쉼표 구분.
--                   비우면 아무 장소도 자격을 얻지 못한다(닫는 쪽으로 틀린다).
--
-- 행을 지워도 DB 에서 사라지지 않는다. ON DUPLICATE KEY UPDATE 는 덮어쓰기만 한다.
-- 테마를 내리려면 행을 지우지 말고 active = 0 으로 둔다.
INSERT INTO supported_theme
    (code, display_name, search_keywords, match_tokens, active, sort_order, created_at, modified_at) VALUES
    ('CHERRY_BLOSSOM',  '벚꽃',    '벚꽃',          '벚꽃',          1, 1, NOW(), NOW()),
    ('FLOWER_FESTIVAL', '꽃축제',  '꽃',            '꽃',            1, 2, NOW(), NOW()),
    ('BEACH',           '해수욕장', '해수욕장,해변', '해수욕장,해변', 1, 3, NOW(), NOW()),
    ('VALLEY',          '계곡',    '계곡',          '계곡',          1, 4, NOW(), NOW()),
    ('AUTUMN_FOLIAGE',  '단풍',    '단풍',          '단풍',          1, 5, NOW(), NOW()),
    ('SILVER_GRASS',    '억새',    '억새',          '억새',          1, 6, NOW(), NOW()),
    ('SNOW_FLOWER',     '눈꽃',    '눈꽃',          '눈꽃',          1, 7, NOW(), NOW()),
    ('SUNRISE',         '해돋이',  '해돋이,일출',   '해돋이,일출',   1, 8, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    search_keywords = VALUES(search_keywords),
    match_tokens = VALUES(match_tokens),
    active = VALUES(active),
    sort_order = VALUES(sort_order),
    modified_at = NOW();

-- 지원 테마 동의어 시드 (#54).
--
-- normalized_form 은 synonym 에서 공백을 지우고 소문자로 내린 형태다(ThemeResolver.normalize).
-- 유니크 제약이 걸려 있어 한 말이 두 테마를 가리킬 수 없다. 정규화하면 같아지는 표기는
-- (`꽃축제` 와 `꽃 축제`) 한 줄만 둔다. 검색어도 정규화해서 맞추므로 둘 다 걸린다.
-- SupportedThemeSeedTest 가 두 컬럼이 어긋나지 않는지 확인한다.
INSERT INTO supported_theme_synonym
    (theme_code, synonym, normalized_form, created_at, modified_at) VALUES
    ('CHERRY_BLOSSOM',  '벚꽃',          '벚꽃',          NOW(), NOW()),
    ('CHERRY_BLOSSOM',  '벚꽃길',        '벚꽃길',        NOW(), NOW()),
    ('CHERRY_BLOSSOM',  '벚꽃축제',      '벚꽃축제',      NOW(), NOW()),
    ('CHERRY_BLOSSOM',  '벚나무',        '벚나무',        NOW(), NOW()),
    ('CHERRY_BLOSSOM',  'cherryblossom', 'cherryblossom', NOW(), NOW()),
    ('FLOWER_FESTIVAL', '꽃축제',        '꽃축제',        NOW(), NOW()),
    ('FLOWER_FESTIVAL', '꽃놀이',        '꽃놀이',        NOW(), NOW()),
    ('FLOWER_FESTIVAL', '플라워축제',    '플라워축제',    NOW(), NOW()),
    ('FLOWER_FESTIVAL', 'flowerfestival', 'flowerfestival', NOW(), NOW()),
    ('BEACH',           '해수욕장',      '해수욕장',      NOW(), NOW()),
    ('BEACH',           '해변',          '해변',          NOW(), NOW()),
    ('BEACH',           '바닷가',        '바닷가',        NOW(), NOW()),
    ('BEACH',           '비치',          '비치',          NOW(), NOW()),
    ('BEACH',           '해수욕',        '해수욕',        NOW(), NOW()),
    ('BEACH',           'beach',         'beach',         NOW(), NOW()),
    ('VALLEY',          '계곡',          '계곡',          NOW(), NOW()),
    ('VALLEY',          '계곡물',        '계곡물',        NOW(), NOW()),
    ('VALLEY',          'valley',        'valley',        NOW(), NOW()),
    ('AUTUMN_FOLIAGE',  '단풍',          '단풍',          NOW(), NOW()),
    ('AUTUMN_FOLIAGE',  '단풍놀이',      '단풍놀이',      NOW(), NOW()),
    ('AUTUMN_FOLIAGE',  '가을단풍',      '가을단풍',      NOW(), NOW()),
    ('AUTUMN_FOLIAGE',  'autumnleaves',  'autumnleaves',  NOW(), NOW()),
    ('SILVER_GRASS',    '억새',          '억새',          NOW(), NOW()),
    ('SILVER_GRASS',    '억새밭',        '억새밭',        NOW(), NOW()),
    ('SILVER_GRASS',    '억새풀',        '억새풀',        NOW(), NOW()),
    ('SILVER_GRASS',    'silvergrass',   'silvergrass',   NOW(), NOW()),
    ('SNOW_FLOWER',     '눈꽃',          '눈꽃',          NOW(), NOW()),
    ('SNOW_FLOWER',     '눈꽃축제',      '눈꽃축제',      NOW(), NOW()),
    ('SNOW_FLOWER',     '설경',          '설경',          NOW(), NOW()),
    ('SNOW_FLOWER',     'snowflower',    'snowflower',    NOW(), NOW()),
    ('SUNRISE',         '해돋이',        '해돋이',        NOW(), NOW()),
    ('SUNRISE',         '일출',          '일출',          NOW(), NOW()),
    ('SUNRISE',         '해뜨는곳',      '해뜨는곳',      NOW(), NOW()),
    ('SUNRISE',         'sunrise',       'sunrise',       NOW(), NOW())
ON DUPLICATE KEY UPDATE
    theme_code = VALUES(theme_code),
    synonym = VALUES(synonym),
    modified_at = NOW();

-- 대체지 큐레이션(alternative_curation) 은 시드를 두지 않는다 (#54).
--
-- 큐레이션은 운영 판단이라 기본값이 없다. 빈 테이블이 정상이며, 비어 있으면 대체지
-- 후보는 추천 자격 판정 결과 그대로다. 예시는 테스트 fixture 에 있다.
