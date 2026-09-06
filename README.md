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
