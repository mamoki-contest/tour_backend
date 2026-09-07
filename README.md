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
