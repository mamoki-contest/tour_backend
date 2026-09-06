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

## 외부 API

| 공급자 | 용도 | 환경변수 |
| --- | --- | --- |
| KorService2 | 관광지 기본정보·검색 | `KOR_SERVICE_KEY` |
| 한국관광콘텐츠랩 | 예측·연관·중심관광지 순위 | `VISIT_KOREA_KEY` (미신청) |

공공데이터포털 인증키는 **Encoding 키**를 그대로 넣습니다. 이미 URL 인코딩된 문자열이라
한 번 더 인코딩하면 인증에 실패하므로, `KorServiceClient` 가 이 값만 인코딩하지 않고 붙입니다.

일부 네트워크에서 `apis.data.go.kr` 의 HTTPS 핸드셰이크가 실패합니다.
그런 환경에서는 `KOR_SERVICE_BASE_URL` 로 http 를 지정합니다.

실제 외부 API 를 호출하는 스모크 테스트는 기본적으로 실행되지 않습니다.

```bash
RUN_EXTERNAL_API_TEST=true ./gradlew test
```
