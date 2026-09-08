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
