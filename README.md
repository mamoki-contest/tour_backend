# tour_backend

한사나다 강원 관광 탐색 웹 - 백엔드 (Java 21 / Spring Boot / AWS)

## 로컬 개발 환경

### 1. MySQL 스키마 생성

개발용과 테스트용 스키마를 나눕니다. 테스트는 `ddl-auto: create-drop` 이라
대상 스키마의 테이블을 매번 삭제하므로, 반드시 분리해야 합니다.

```sql
CREATE DATABASE tour CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE tour_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 환경변수 설정

`.env.example` 을 `.env` 로 복사한 뒤 각자 환경에 맞게 채웁니다.
`.env` 는 `.gitignore` 에 등록되어 있어 커밋되지 않습니다.

```bash
cp .env.example .env
```

| 변수 | 설명 |
| --- | --- |
| `DB_HOST` | MySQL 호스트 |
| `DB_PORT` | MySQL 포트 |
| `DB_NAME` | 개발용 스키마 (`tour`) |
| `DB_USERNAME` | 계정 |
| `DB_PASSWORD` | 비밀번호 |
| `TEST_DB_NAME` | 테스트용 스키마 (`tour_test`) |

### 3. 실행

```bash
./gradlew bootRun
```

```bash
./gradlew test
```

## 적재·수집 작업

카탈로그·언급량·TMAP·입장객 적재는 커맨드라인으로 실행합니다.

```bash
java -jar build/libs/tour-0.0.1-SNAPSHOT.jar --job=catalog
java -jar build/libs/tour-0.0.1-SNAPSHOT.jar --job=mention --month=202609
java -jar build/libs/tour-0.0.1-SNAPSHOT.jar --job=tmap --dir=sample --downloaded-on=2026-09-06
java -jar build/libs/tour-0.0.1-SNAPSHOT.jar --job=visitor-stats --file="sample/주요관광지점 입장객(2004.07 이후)_260918083154.xls"
```

`--job` 을 주지 않으면 어떤 작업도 실행되지 않고 평소대로 서버만 뜹니다.

| 작업 | 하는 일 | 추가 인자 |
| --- | --- | --- |
| `catalog` | 강원 관광지 카탈로그 적재 | 없음 |
| `mention` | 온라인 언급량 수집 | `--month` (비우면 이번 달) |
| `tmap` | TMAP 검색순위 zip 묶음 적재 | `--dir` 필수, `--downloaded-on` |
| `visitor-stats` | 주요관광지점 입장객통계 엑셀 적재 | `--file` 필수, `--downloaded-on` |

**`catalog` 을 먼저 돌려야 합니다.** 언급량 수집은 카탈로그를 순회하고, TMAP·입장객은 카탈로그의 `contentId` 에 매칭되므로 카탈로그가 비어 있으면 수집이 멈추거나 매칭이 0건이 됩니다.

관리 API 는 두지 않았습니다. 이 서비스에는 인증 체계가 없어서(PRD 가 회원가입·서버 계정을 범위 밖으로 둠) 관리 API 를 열면 누구나 호출해 외부 API 를 소진시키거나 스냅샷을 갈아치울 수 있습니다.

## API 문서

앱을 띄운 뒤 아래에서 확인합니다.

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI 스펙: <http://localhost:8080/v3/api-docs>

## 프로파일

| 프로파일 | 대상 스키마 | `ddl-auto` | 비고 |
| --- | --- | --- | --- |
| `local` (기본) | `tour` | `update` | 로컬 개발 |
| `test` | `tour_test` | `create-drop` | 테스트 실행 시에만 사용 |
| `prod` | AWS RDS | `update` | 환경변수로 접속 정보 주입 |

배포 서버 DB 를 대상으로 테스트를 실행하지 않습니다.
`TestProfileDatabaseTest` 가 테스트 접속 대상이 `tour_test` 인지 검증합니다.

## 규칙

- 마이그레이션 도구를 쓰지 않고 `ddl-auto` 로 스키마를 관리합니다.
  컬럼 이름 변경·삭제는 반영되지 않으므로 DB 재생성이나 수동 `ALTER` 로 처리합니다.
- 운영 판단이 필요한 표(지역코드, 지원 테마, 대체지 큐레이션)는 관리 API 없이
  `src/main/resources/data.sql` 시드로 관리합니다. 아래 시드 표 절을 참고하세요.
- 결측 값은 `0` 이나 빈 문자열로 채우지 않고 `null` 과 `DataStatus.NO_DATA` 로 구분합니다.
- 모든 응답은 `RsData` 봉투를 사용하며, 예외는 `GlobalExceptionHandler` 가 변환합니다.
- API 키와 DB 접속 정보는 저장소에 커밋하지 않습니다.
- 외부 공급자 장애는 오류가 아니라 정상 응답의 한 상태입니다.
  `ExternalApiCacheService` 가 흡수해 `STALE`(최종 정상 데이터) 또는 `NO_DATA` 로 변환하며,
  `ExternalApiException` 이 컨트롤러까지 전파되지 않습니다.
- 관광지별 방문 혼잡도 예측은 **그 장소 자신의 30일 분포 안에서만** 해석합니다.
  서로 다른 관광지의 예측값을 절대 혼잡도 순위로 쓰지 않습니다. 자세한 규칙은 아래 참고.

## 날짜 탐색 (`dateMode`)

`GET /api/v1/attractions` 에 `dateMode` 를 주면 항목마다 `visitTiming` 이 붙습니다.
비우면 예측을 조회하지 않고 목록만 돌려줍니다.

| 모드 | 선택일 | 응답 |
| --- | --- | --- |
| `FIXED` | `visitDate` 필수 | 그 날의 `status` (`LOW`/`NORMAL`/`HIGH`) |
| `FLEXIBLE` | 받지 않음 | 향후 30일 중 `quietestDate` |

공급자(`TatsCnctrRateService`)가 집중률의 공식 등급 기준을 주지 않으므로,
값 자체를 한산·보통·혼잡으로 끊지 않고 **그 장소의 향후 30일 분포 안에서의 위치**로만 판정합니다.
규칙은 `VisitTimingResolver` 에 상수로 고정되어 있습니다.

- 정렬한 유효 예측의 하위 1/3 은 `LOW`, 상위 1/3 은 `HIGH`, 나머지는 `NORMAL`.
  값이 같은 날이 다른 등급을 받지 않도록 순번이 아니라 값으로 끊습니다.
- 유효 예측일이 10일(30일의 1/3) 미만이면 판정하지 않고 `NO_DATA`.
- 같은 값이 분포의 대부분을 덮어 두 경계가 붙으면 그 값이 이 장소의 `NORMAL` 이고,
  그보다 낮은 날만 `LOW`, 높은 날만 `HIGH` 입니다.
- 유연 모드는 30일 예측이 모두 같을 때 `NO_DATA` 입니다.
  더 한산한 날이 없는데 하나를 골라 주지 않습니다.
- 지원 범위(오늘 ~ +29일) 밖의 미래 날짜는 400 이 아니라 200 + `OUT_OF_RANGE` 로 안내합니다.
  과거 날짜와 형식 오류는 400 입니다.

원본 예측값은 응답에 담지 않고, 예측값 기준 정렬 파라미터도 제공하지 않습니다.
값을 노출하면 서로 다른 관광지를 그 값으로 줄 세우게 되기 때문입니다.

조회 단위가 관광지 단건이 아니라 **시·군**이라, 목록에 등장한 시·군 수만큼만 호출합니다.
응답은 24시간 캐시하므로 강원 전체를 훑어도 하루 호출 수가 시·군 18개를 넘지 않습니다.

## 관광지 상세 (`GET /api/v1/attractions/{contentId}`)

기본정보, 30일 방문 혼잡도 예측, 대체지 후보, 함께 가기 좋은 곳을 각각 독립 필드로 돌려줍니다.
하나의 점수로 합치지 않고, 어느 하나의 결측을 다른 신호로 추정하지 않습니다.

기본정보를 얻지 못하면 무엇에 대한 상세인지 말할 수 없으므로 404 입니다.
예측이나 연관 장소가 비어 있는 것은 오류가 아니라 정상 응답의 한 상태입니다.

### 30일 예측

`visitTiming` 은 이 장소의 한산 예상일, `dailyForecast` 는 지원 범위 30일의 하루치 판정입니다.
목록의 확정 모드와 같은 분포·같은 경계를 쓰므로 같은 날 판정이 어긋나지 않습니다.
여기서도 집중률 원본값은 담지 않습니다.

### 대체지 후보 (`alternatives`) 와 함께 가기 좋은 곳 (`companions`)

`TarRlteTarService1` 의 연관 장소를 대분류(`rlteCtgryLclsNm`)로 나눕니다.
실제 응답에서 확인된 값은 `관광지` · `음식` · `숙박` 세 가지입니다.

| 대분류 | 유형 | 묶음 |
| --- | --- | --- |
| 관광지 | `ATTRACTION` | 자격을 충족하면 `alternatives` |
| 음식 | `RESTAURANT` | `companions` |
| 숙박 | `LODGING` | `companions` |
| 그 외 | `OTHER` | 어느 쪽에도 넣지 않음 |

대체지 후보는 다음을 **모두** 만족해야 합니다.

- 대분류가 관광지인 곳
- 원래 장소와 다른 곳 (정규화한 이름으로 판단. 공급자가 기준 관광지를 자기 연관 목록에 넣기도 합니다)
- 유효한 방문 혼잡도 예측을 가진 곳 (`NO_DATA` · `OUT_OF_RANGE` 는 자격 미달)

예측이 없는 곳은 연관 순위가 1위여도 담기지 않습니다. 한산하다는 근거 없이 사람을 보내지
않기 위한 자격이며, 순위나 큐레이션이 이를 우회하지 못합니다.

자격 판정이 끝난 뒤 `alternative_curation` 표의 운영 판단을 적용합니다. 순서가 뒤바뀌면
큐레이션이 자격을 만들어 줄 수 있어 뒤로 고정했습니다. 규칙은 아래 시드 표 절에 있습니다.

### 빈 목록의 해석

두 묶음 모두 `status` 로 비어 있는 이유를 구분합니다. 같은 문구로 표시하지 마세요.

| status | 뜻 |
| --- | --- |
| `AVAILABLE` | 항목 있음 |
| `NO_RELATED_DATA` | 공급자 응답을 얻지 못했거나 이 관광지가 연관 목록에 없음 |
| `NONE_QUALIFIED` | 연관 장소는 받았으나 자격을 충족한 곳이 없음 |

연관 장소도 조회 단위가 **시·군**이고 24시간 캐시합니다. 연관 장소는 다른 시·군일 수 있어
`lawdCode` 와 `regionName` 을 함께 내려줍니다.

### 알려진 한계 — 공급자 간 이름이 거의 겹치지 않습니다

`TarRlteTarService1` 은 표준 관광지 식별자를 주지 않아 시·군 안에서 정규화한 이름으로
매칭합니다. 그런데 두 공급자가 같은 장소를 다르게 적습니다.

강릉시(51150) 를 실제로 조회해 비교한 결과입니다 (2026-09-08, `baseYm=202607`).

```
TarRlteTar 기준 관광지        68곳
KorService2 강릉 항목        675건

연관 데이터가 붙는 KorService2 항목
  관광지(12)      5 / 48   10.4%
  레포츠(28)      2 / 27    7.4%
  쇼핑(38)        1 / 31    3.2%
  숙박(32)        1 / 156   0.6%
  음식점(39)      1 / 401   0.2%
  전체           10 / 675   1.5%
```

`경포해변`(TarRlteTar) 과 `경포해수욕장`(KorService2) 처럼 이름 자체가 다르고,
`동부시장` 과 `강릉 동부시장` 처럼 시·군 접두어도 어긋납니다. 다만 접두어를 잘라 봐도
매칭은 10곳에서 11곳으로 늘 뿐이라, 표기 정규화로 풀리는 문제가 아닙니다.

**대부분의 관광지 상세는 `NO_RELATED_DATA` 를 받습니다.** 이것이 값을 지어내지 않는
정상 동작이며, 그래서 빈 목록과 정보 없음을 상태로 구분합니다. 커버리지를 올리려면
좌표 기반 매칭이나 별도 매핑 테이블이 필요하며, 이는 별도 논의 대상입니다.

## 시드로 관리하는 표

운영자 화면은 PRD 범위 밖이고 관리 API 도 두지 않았으므로(인증 체계가 없습니다),
운영 판단이 들어가는 표는 `src/main/resources/data.sql` 로 관리합니다.
이 파일은 `spring.jpa.defer-datasource-initialization: true` 덕분에 스키마 생성 뒤,
**매 기동마다** 실행됩니다. `ON DUPLICATE KEY UPDATE` 라 다시 적재해도 중복되지 않습니다.

**시드는 덮어쓰기만 합니다.** 파일에서 행을 지워도 DB 에서는 사라지지 않습니다.
내리려면 행을 지우지 말고 비활성 표시(`supported_theme.active = 0`)를 쓰거나 직접 삭제합니다.

### 지원 테마 — `supported_theme` · `supported_theme_synonym`

검색어를 지원 테마로 잇는 데 쓰는 표입니다. 코드 상수(`SupportedTheme`)는 테마 **코드**만
가지고, 표시명·동의어·공급자 검색어·자격 토큰은 이 표에 있습니다.

| 테이블 | 컬럼 | 뜻 |
| --- | --- | --- |
| `supported_theme` | `code` | 테마 코드. `SupportedTheme` 상수 이름이며 응답의 `appliedTheme.code` 값입니다 |
| | `display_name` | 화면에 보여줄 이름 |
| | `search_keywords` | 공급자(KorService2)에게 보낼 검색어. 쉼표 구분 |
| | `match_tokens` | 추천 자격. 장소 이름에 이 중 하나가 들어가야 테마 결과에 담습니다. 쉼표 구분 |
| | `active` | `0` 이면 검색어로 이어지지도, 제안으로 나오지도 않습니다 |
| | `sort_order` | 거리가 같은 제안끼리의 순서 |
| `supported_theme_synonym` | `theme_code` | 가리키는 테마 코드 |
| | `synonym` | 사용자가 입력할 법한 표기 |
| | `normalized_form` | 공백을 지우고 소문자로 내린 형태. **유니크** |

`ThemeResolver` 가 **기동 시 한 번** 읽어 메모리에 둡니다. 테마는 요청마다 조회할 값이
아닙니다. 시드를 고쳤으면 애플리케이션을 다시 띄워야 반영됩니다.

- `normalized_form` 에 유니크 제약을 둬 한 말이 두 테마를 가리킬 수 없게 합니다.
  정규화하면 같아지는 표기(`꽃축제` 와 `꽃 축제`)는 한 줄만 둡니다. 검색어도 정규화해서
  맞추므로 둘 다 걸립니다.
- 동의어와 정확히 같거나 **한 글자 차이**까지만 그 테마로 봅니다. 두 글자 이상 다르면
  다른 말입니다. 넓게 잡을수록 엉뚱한 검색어가 지원 테마로 둔갑하고, 그 순간
  `SUPPORTED_THEME` 표시가 보증하는 것이 없어집니다.
- **표가 비어 있으면 모든 검색이 `GENERAL_SEARCH` 로 떨어집니다.** 오류가 아닙니다.
  코드에 남은 옛 목록으로 대신하지 않습니다. 시드를 지웠는데도 지원 테마 표시가 계속
  붙으면 그 표시가 무엇을 근거로 하는지 알 수 없게 되기 때문입니다.
- `match_tokens` 가 비면 그 테마는 아무 장소도 담지 못합니다. 값이 빠졌을 때 전부
  통과시키는 대신 닫는 쪽으로 틀립니다.
- `search_keywords` 가 비면 그 테마는 목록에서 아예 빠집니다. 남겨 두면 "부를 것이
  없었다" 가 "공급자가 답하지 않았다"(`NO_DATA`) 로 보고됩니다.
- `SupportedTheme` 에 없는 코드가 시드에 있으면 경고를 남기고 건너뜁니다.
  위 세 경우 모두 기동 로그에 경고가 남습니다. 증상이 "검색이 그냥 안 걸린다" 뿐이라
  기록이 없으면 한참 뒤에야 드러납니다.

`SupportedThemeSeedTest` 가 시드 8개 적재, `normalized_form` 과 실제 정규화 결과의 일치,
동의어 중복, 그리고 단위 테스트가 쓰는 고정 목록과의 일치를 확인합니다.

### 대체지 큐레이션 — `alternative_curation`

기준 관광지 하나의 대체지 후보 목록에서 특정 장소를 빼거나 대표로 앞세웁니다.

| 컬럼 | 뜻 |
| --- | --- |
| `base_content_id` | 기준 관광지의 표준 식별자. 이 관광지의 상세에서만 적용됩니다 |
| `target_normalized_name` | 대상 연관 장소명을 `PlaceNameNormalizer` 로 정규화한 값 |
| `target_lawd_code` | 대상의 법정동 시·군 코드 5자리. 같은 이름이 여러 시·군에 있어 필수입니다 |
| `action` | `EXCLUDE` (뺀다) / `REPRESENTATIVE` (맨 앞에 둔다) |
| `theme_code` | 적용할 지원 테마. 비우면 테마와 무관하게 적용합니다 |
| `reason` | 이 판단의 근거. 필수입니다 |
| `created_at` | 등록일 (`BaseEntity`) |

**시드는 비어 있습니다.** 큐레이션은 운영 판단이라 기본값이 없고, 빈 표는 대체지 후보가
추천 자격 판정 결과 그대로라는 뜻입니다. 예시는 테스트 fixture 에 있습니다.

적용 규칙:

- **추천 자격 판정이 끝난 뒤**에만 적용합니다. 자격 미달 후보는 이 지점까지 오지 않으므로
  큐레이션이 되살릴 수 없습니다 (CONTEXT `대체지 큐레이션`: 추천 자격을 만들거나 결측
  혼잡도 데이터를 대신하지 않습니다).
- `EXCLUDE` 는 자격을 충족한 후보를 뺍니다. `REPRESENTATIVE` 는 맨 앞으로 옮기며,
  대표끼리도 나머지도 공급자 연관 순위 순서를 그대로 지킵니다.
- 한 대상에 두 판단이 걸리면 `EXCLUDE` 가 이깁니다. 부적절하다는 판단과 대표로 삼겠다는
  판단이 부딪힐 때는 빼는 쪽이 덜 위험합니다.
- **`theme_code` 가 적힌 행은 지금 적용되지 않습니다.** 관광지 상세에는 테마 맥락이 없어서,
  적용하면 그 테마 밖에서까지 판단이 새어 나갑니다. 테마별 대체지 경로가 생기면 살아납니다.
- 대상을 표준 관광지 식별자가 아니라 이름 + 시·군으로 가리킵니다. 연관 장소 공급자가
  식별자를 주지 않기 때문이며, 매핑 테이블이 생기면 대상 표현도 함께 옮깁니다.
- 큐레이션으로 후보가 모두 빠지면 `status` 는 `NONE_QUALIFIED` 입니다. 자격 미달로 비었을
  때와 같은 값이라 화면에서 둘을 가릴 수 없습니다. 가려야 할 필요가 생기면 상태를 늘립니다.

## 외부 API

| 서비스 | 용도 |
| --- | --- |
| `KorService2` | 관광지 기본정보·검색 |
| `LocgoHubTarService1` | 시·군 내부 중심관광지 순위 |
| `TatsCnctrRateService` | 향후 30일 방문 혼잡도 예측 |
| `TarRlteTarService1` | 연관 관광지·음식점·숙박 |
| `DataLabService` | 지역별 방문자수 |

모두 공공데이터포털에서 제공하며 **인증키 하나(`KOR_SERVICE_KEY`)를 공유**합니다.
계정당 인증키는 하나이고, 서비스별로는 활용신청으로 권한만 붙습니다.

관광지 관심도는 API 가 아니라 한국관광 데이터랩 공식 CSV/Excel 로 적재합니다.
기존 관광빅데이터정보서비스 API 는 폐기되었습니다.

인증키는 **Encoding 키**를 그대로 넣습니다. 이미 URL 인코딩된 문자열이라
한 번 더 인코딩하면 인증에 실패하므로, `KorServiceClient` 가 이 값만 인코딩하지 않고 붙입니다.

일부 네트워크에서 `apis.data.go.kr` 의 HTTPS 핸드셰이크가 실패합니다.
그런 환경에서는 `KOR_SERVICE_BASE_URL` 로 http 를 지정합니다.

실제 외부 API 를 호출하는 스모크 테스트는 기본적으로 실행되지 않습니다.

```bash
RUN_EXTERNAL_API_TEST=true ./gradlew test
```
