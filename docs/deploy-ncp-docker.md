# NCP 배포 런북 (Docker Compose) — tour_backend

NCP 서버 1대에 **컨테이너 2개**(MySQL, 애플리케이션)를 Docker Compose로 띄웁니다.
이미지는 로컬에서 만들어 Docker Hub에 올리고, 서버는 그걸 받아 실행만 합니다.
**서버에 JDK·Gradle·MySQL을 설치하지 않고, 소스도 두지 않습니다.**

```
[내 PC]  소스 → docker build → Docker Hub (푸시)
                                   ↓ 풀
[NCP 서버]  docker compose up  →  tour-app (8080)  ──▶  tour-db (3306, 내부 전용)
                                        └─ 같은 도커 네트워크. DB_HOST=db
```

> **기존 `docs/deploy-ncp.md` 와 방식이 다릅니다.** 그쪽은 jar를 scp로 올려 systemd로 띄우고
> MySQL을 apt로 설치합니다. 둘 다 유지하면 서버 상태가 갈리므로 **하나만 고르세요.**
> 이 문서대로 간다면 기존 문서는 지우거나 "이전 방식"으로 표시해 두는 게 좋습니다.

| 항목 | 값 |
| --- | --- |
| 환경 | NCP VPC |
| 서버 OS | Ubuntu Server 22.04 / 24.04 |
| 런타임 | Docker Engine + Compose plugin |
| 앱 이미지 | `eclipse-temurin:21-jre` 기반, 멀티스테이지 빌드 |
| DB 이미지 | `mysql:8.0` |
| 프로파일 | `prod` (`ddl-auto: update`) |
| 공개 포트 | 8080만 |

---

## 재배포 체크리스트 (2026-09-21 기준)

> **처음 배포하는 거라면 이 절을 건너뛰고 [0. 로컬 준비](#0-로컬-준비)부터 차례로 읽으세요.**
> 이 절은 이미 서버가 떠 있는 상태에서 **다시 올릴 때** 밟을 순서만 모은 목차입니다.
> 각 단계가 왜 그런지는 본문에 있고, 여기서는 링크로만 잇습니다.

**왜 다시 올려야 하나.** 지금 `http://211.233.201.61:8080` 에 떠 있는 빌드는 **PR #97 이전**
것입니다. 그 뒤에 들어간 대표 사진 보강(#100), 검색 상태 노출 같은 변경이 빠져 있어
**이미지를 다시 만들어 다시 올려야** 합니다. 컨테이너만 재시작해서는 바뀌지 않습니다.

### 배포 기준은 `develop` 입니다

**`main` 에는 초기 커밋만 있습니다.** 팀의 작업은 전부 `develop` 에 쌓였고, 이 런북이 말하는
"최신" 은 언제나 `develop` 입니다. 빌드도 `develop` 을 체크아웃한 상태에서 합니다.

```bash
git checkout develop && git pull origin develop
git log --oneline -1          # 기준 커밋을 적어 두세요 (2026-09-21 시점: 167a40c)
```

> `main` 을 정리하고 싶다면 한 번 `develop` → `main` 을 머지해 두는 쪽을 권합니다.
> 지금 그대로 두면 저장소를 처음 받는 사람이 빈 `main` 을 보고 코드가 없다고 판단합니다.
> 다만 **이 런북은 `main` 이 비어 있는 채로도 성립합니다** — 배포 기준이 `develop` 이라는
> 것만 지키면 됩니다.

### 0. 저장소 밖에서 전달받아야 할 것

**저장소를 받는 것만으로는 배포할 수 없습니다.** 코드에 없는 것이 세 종류 있고, 그중 둘은
이미 끝나 있습니다. 먼저 이 절을 읽고 없는 것을 요청하세요.

**(1) API 키 다섯 개 — 전달받아야 합니다.**

| 키 | 무엇에 쓰나 |
| --- | --- |
| `KOR_SERVICE_KEY` | 공공데이터포털. 카탈로그·방문 규모·주차·연관 장소 등 대부분 |
| `NAVER_API_HUB_KEY_ID` | NAVER API HUB 인증 정보 (Client ID) |
| `NAVER_API_HUB_KEY` | NAVER API HUB 인증 정보 (Client Secret) |
| `KAKAO_REST_API_KEY` | 장소 매핑(서버 전용). 지도 표시와 다른 키입니다 |
| `ITS_API_KEY` | 국가교통정보센터. 상세의 도로 소통 |

> **값은 안전한 경로로 받으세요 — 이슈·PR·채팅에 붙여 넣지 마세요.** 한 번 올라간 값은
> 지운 뒤에도 히스토리·알림 메일에 남습니다. 비밀번호 관리자의 공유 기능, 암호를 건 파일,
> 직접 만나 옮기기 중 하나를 쓰세요.
>
> 받은 값은 `.env.docker` 에만 넣습니다. 이 파일은 `.gitignore` 에 걸려 있어 커밋되지 않고,
> 서버에 올린 뒤 `chmod 600` 으로 잠급니다. **이 런북을 포함해 어떤 문서에도 값을 적지
> 마세요** — 이 문서가 키 이름만 적고 값을 적지 않는 이유입니다.
>
> 프론트엔드에 필요한 `KAKAO_MAP_APP_KEY`(카카오맵 **JavaScript** 키)는 여기 목록에
> 없습니다. 백엔드가 쓰지 않습니다 — `tour_contest` 의 `frontend/DEPLOY.md` 를 보세요.

**(2) 공급자 콘솔 작업 — 대부분 끝나 있습니다.**

| 작업 | 상태 |
| --- | --- |
| 공공데이터포털 활용신청 (KorService2 · TatsCnctrRate · LocgoHubTar · TarRlteTar · DataLab · **GNits 강릉 실시간 주차**) | **완료.** 키를 받는 즉시 씁니다 |
| NAVER API HUB Application 에 `NAVER 검색 > 이미지` 활성화 | **완료.** 대표 사진 보강(8-2-5)이 바로 돕니다 |
| 카카오 개발자 콘솔에 **배포 도메인 등록** | **남아 있습니다.** 다만 **프론트엔드 쪽 작업**이고, 프론트를 배포해 주소가 나와야 할 수 있습니다. 콘솔이 **사용자 계정**이라 담당자가 직접 들어갈 수 없으니 주소가 정해지면 사용자에게 등록을 요청하세요 |

키를 받았는데 데이터가 비어 온다면 키 문제이지 활용신청 문제가 아닐 가능성이 높습니다.
그래도 오류 없이 한 종류만 비는 증상이면 "막혔을 때" 표를 보세요.

**(3) 데이터 파일 — 전달받을 것이 없습니다.**

- **파일로 적재하는 세 가지(TMAP zip 18개 · 입장객 XLS · 주차장 CSV)는 저장소의 `sample/`
  에 이미 커밋돼 있습니다.** 따로 받을 필요 없이 그대로 서버에 올려 쓰면 됩니다
  (8-2-1 의 `scp` 명령이 `sample/` 을 가리키는 이유입니다). 받는 조건과 갱신 주기는
  `sample/README.md` 에 있습니다.
- **대표 사진은 이미지 파일을 보관하지 않습니다.** `place_image` 표에 **주소(URL)만**
  저장하고 화면이 그 주소를 겁니다. 그래서 옮길 이미지 파일이 없고, 서버에 스토리지를
  붙일 필요도 없습니다. 대신 배치를 **서버에서 한 번 돌려야** 표가 채워집니다(8-2-5).

**(4) 계정 결정 — 사용자와 정하세요.**

- **Vercel 계정을 누가 소유할지.** 프론트엔드를 Vercel 에 올릴지, 올린다면 사용자 계정으로
  할지 담당자 계정으로 할지 먼저 정해야 합니다. 이 결정이 프론트 배포 주소를 정하고, 그
  주소가 **아래 6번의 `CORS_ALLOWED_ORIGINS`** 와 **카카오 콘솔 도메인 등록**에 들어갑니다.
  자세한 것은 `tour_contest` 의 `frontend/DEPLOY.md`.

### 1. `.env.docker` 의 키가 다 채워져 있는지 본다

| 키 | 없으면 생기는 일 | 발급처·전제 |
| --- | --- | --- |
| `DOCKERHUB_USERNAME`·`TAG` | `push`·`pull` 이 실패 | Docker Hub 계정 ([2절](#2-docker-hub-로그인과-푸시)) |
| `DB_HOST`(=`db`)·`DB_PORT`·`DB_NAME`·`DB_USERNAME`·`DB_PASSWORD`·`MYSQL_ROOT_PASSWORD` | 앱이 DB 에 못 붙어 기동 실패 | 직접 정합니다. **`DB_HOST` 는 `db` 고정** |
| `KOR_SERVICE_KEY` | 카탈로그·주차 등 공공데이터가 통째로 비어 옵니다 | 공공데이터포털 Encoding 키 **하나**를 6개 서비스가 공유합니다. 다만 **활용신청은 서비스마다 따로** 해야 하고, **강릉시 실시간 주차(`GNitsTrafficInfoService_1.0`) 도 같은 키지만 별도 활용신청이 필요합니다.** 빠뜨리면 오류 없이 주차 상태만 "정보 없음" 이 됩니다 |
| `NAVER_API_HUB_KEY_ID`·`NAVER_API_HUB_KEY` | 언급량 수집과 대표 사진 보강이 401 | NAVER API HUB. **블로그 검색과 이미지 검색이 같은 키**지만, Application 의 이용 API 에 `NAVER 검색 > 이미지` 를 켜 두지 않으면 사진 보강만 401 로 막힙니다(8-2-5) |
| `KAKAO_REST_API_KEY` | 장소 매핑이 호출을 한 번도 내지 않고 끝납니다 | developers.kakao.com REST API 키. 서버 전용이고 프론트로 내려보내지 않습니다 |
| `ITS_API_KEY` | 상세의 도로 소통이 비어 옵니다 | openapi.its.go.kr 별도 발급 |
| `CORS_ALLOWED_ORIGINS` | 브라우저가 이 API 를 **직접** 부를 때만 막힙니다 | 프론트 배포 주소를 넣습니다. 쉼표로 여럿. 아래 주의 참고 |

> **`CORS_ALLOWED_ORIGINS` 는 지금 프론트에는 필요 없습니다.** 프론트(React Router SSR)는
> 서버 로더에서만 이 API 를 부르고, 브라우저는 백엔드를 직접 부르지 않습니다(서버-서버 호출은
> CORS 대상이 아닙니다). 그래도 **프론트 배포 주소를 넣어 두세요** — 브라우저에서 직접 부르는
> 경로가 나중에 생기거나, 다른 출처의 Swagger UI 로 확인할 때 이 값이 없으면 그때 가서
> 원인을 찾게 됩니다. 값이 비어 있으면 CORS 매핑 자체가 등록되지 않습니다.
>
> **키 값은 이 문서에 적지 않습니다.** `.env.docker` 는 커밋되지 않으며, 서버에 올린 뒤
> `chmod 600` 으로 잠급니다([5-2](#5-서버에-올리고-실행)).

### 2. 이미지를 다시 만들어 푸시한다 — [2-3](#2-docker-hub-로그인과-푸시)

```bash
# 내 PC, develop 체크아웃 상태에서
docker compose --env-file .env.docker build app
docker compose --env-file .env.docker push app
```

`TAG` 를 `latest` 로만 쓰면 서버에 어떤 빌드가 떠 있는지 나중에 구분할 수 없습니다.
이번 재배포처럼 "무엇이 올라가 있는지 몰라서 다시 올리는" 일을 막으려면 `TAG` 를 올리세요.

### 3. 서버에서 받아 띄운다 — [6절](#6-재배포)

```bash
# 서버
docker compose -f docker-compose.prod.yml --env-file .env.docker pull
docker compose -f docker-compose.prod.yml --env-file .env.docker up -d
```

### 4. 적재·수집을 순서대로 돌린다 — [8-1](#8-1-순서대로-손으로-한-번-돌린다)

**순서가 있습니다. `place-image` 가 `place-mapping` 다음, `mention` 앞입니다.**

```
catalog → 파일 적재(parking-catalog · tmap · visitor-stats) → place-mapping → place-image → mention
```

| # | 명령 | 비고 |
| --- | --- | --- |
| 1 | `--job=catalog` | 나머지가 이것을 딛고 섭니다 |
| 2 | `--job=parking-catalog --file=...` | 파일 필요. 8-2-2 |
| 3 | `--job=tmap --dir=...` | zip **18개** 전부. 8-2-2 |
| 4 | `--job=visitor-stats --file=...` | 파일 필요. 8-2-2 |
| 5 | `--job=place-mapping` | 확정이 나오면 3·4 를 한 번 더. 8-2-4 |
| 6 | `--job=place-image` | **약 5분.** 네이버 이미지 API 활성화 전제. 8-2-5 |
| 7 | `--job=mention` | 카탈로그 수천 곳을 돌아 **오래 걸립니다** |

### 5. 올라갔는지 눈으로 확인한다

| 확인 | 기대값 (2026-09-21 로컬 실측) |
| --- | --- |
| `http://<공인IP>:8080/v3/api-docs` | `200` |
| `http://<공인IP>:8080/api/v1/regions/visit-scale` | 강원 18개 시·군, `dataStatus: "AVAILABLE"` |
| `http://<공인IP>:8080/api/v1/attractions?size=1` | `totalCount: 4741` |
| `http://<공인IP>:8080/api/v1/attractions/125790` (강릉 경포대) | `currentAccess.parking.status: "AVAILABLE"`, `lots` 안에 `source: "강릉시 교통정보 조회서비스"` 인 실시간 주차장이 섞여 있음 |
| `http://<공인IP>:8080/api/v1/attractions?size=50` | `imageSource` 가 `KOR_SERVICE` 와 `NAVER_IMAGE` 로 섞여 나옴. 전부 `KOR_SERVICE` 이고 `null` 이 보이면 6번(`place-image`)을 안 돌린 것입니다 |

사진 보강이 실제로 몇 건 들어갔는지는 표를 직접 세는 쪽이 정확합니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker exec db \
  mysql -u tour -p tour -e "SELECT status, COUNT(*) FROM place_image GROUP BY status;"
```

```
AVAILABLE  1131
NONE         52
```

로컬 실측값입니다. `FAILED` 가 있거나 합이 1,183 에 크게 못 미치면 배치가 중간에 끊긴
것이니 8-2-5 를 보세요.

### 6. 프론트 배포 주소를 `CORS_ALLOWED_ORIGINS` 에 넣는다

프론트를 배포한 뒤에 주소가 정해지므로 마지막입니다. `.env.docker` 만 고치고 앱 컨테이너를
다시 만듭니다(이미지를 다시 받을 필요는 없습니다).

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker up -d --force-recreate app
```

---

## 파일 구성

이번에 추가된 것들입니다.

| 파일 | 역할 | 커밋 |
| --- | --- | --- |
| `Dockerfile` | 멀티스테이지 빌드(JDK로 빌드 → JRE로 실행) | O |
| `docker-compose.yml` | 로컬: 소스를 빌드해 띄움 | O |
| `docker-compose.prod.yml` | 서버: Docker Hub 이미지를 받아 띄움 | O |
| `.dockerignore` | 빌드 컨텍스트에서 `.env`·`build/`·`sample/` 제외 | O |
| `.env.docker.example` | 환경변수 템플릿 | O |
| `.env.docker` | **실제 값. 커밋되지 않습니다.** | X |

`.gitignore` 에 `!.env.*.example` 을 추가했습니다. 이게 없으면 `.env.*` 규칙에 걸려
템플릿까지 커밋되지 않습니다.

---

## 0. 로컬 준비

> **PowerShell에서 실행할 때 주의 (Windows 로컬 한정, 서버는 해당 없음)**
>
> | bash 표기 | PowerShell에서는 |
> | --- | --- |
> | `curl ...` | **`curl.exe ...`** — `curl` 은 `Invoke-WebRequest` 별칭이라 옵션이 안 먹습니다 |
> | `... \| grep foo` | `docker images --filter "reference=*/foo"` 또는 `... \| Select-String foo` |
> | `cp a b` | `Copy-Item a b` (또는 `cp` 별칭 그대로 가능) |
> | `A && B` | `A; if ($?) { B }` — PowerShell 5.1에는 `&&` 가 없습니다 |
>
> 헷갈리면 이 저장소 폴더에서 **Git Bash** 를 열어 문서의 명령을 그대로 쓰는 게 편합니다.
> 서버(Ubuntu)에서 실행하는 명령은 전부 bash 라 그대로 쓰면 됩니다.

**Docker Desktop이 실행 중이어야 합니다.**

```bash
docker version
```

`Server:` 섹션까지 나오면 준비된 겁니다. `cannot find the file specified` 가 나오면
Docker Desktop을 실행하세요.

<details>
<summary>Docker Desktop이 <code>An unexpected error occurred</code> 로 죽는다면</summary>

오류 본문이 아래 형태면 **`Reset to factory defaults` 를 누르지 마세요.** 이미지·설정이 모두 날아가는데
그럴 필요가 없는 문제입니다.

```
initializing ... : listening on unix://.../xxx.sock:
rename .../xxx.sock .../xxx.sock.stale: The file cannot be accessed by the system.
```

이전에 비정상 종료하면서 남은 유닉스 소켓 파일이 원인입니다. 이 파일들은 삭제도 이름 변경도
되지 않지만, **상위 폴더는 이름을 바꿀 수 있습니다.** Docker가 폴더를 새로 만듭니다.

PowerShell에서:

```powershell
Get-Process -Name "Docker Desktop","com.docker.backend" -EA SilentlyContinue | Stop-Process -Force
$ts = Get-Date -Format yyyyMMddHHmmss
foreach ($d in @("$env:LOCALAPPDATA\Docker\run", "$env:LOCALAPPDATA\docker-secrets-engine")) {
  if (Test-Path $d) { Rename-Item $d ((Split-Path $d -Leaf) + ".broken-$ts") }
}
```

그다음 Docker Desktop을 다시 실행합니다. 오류 메시지의 소켓 경로가 매번 달라지면
그 경로의 상위 폴더도 같은 방식으로 치웁니다. **재부팅하면 한 번에 정리됩니다.**

`.broken-*` 폴더는 재부팅 뒤 지우면 됩니다.

</details>

환경변수 파일을 만듭니다.

```bash
cp .env.docker.example .env.docker
```

`.env.docker` 를 열어 **최소 이 다섯 개**를 채웁니다.

| 키 | 값 |
| --- | --- |
| `DOCKERHUB_USERNAME` | Docker Hub 아이디 (지금 `CHANGEME`) |
| `DB_PASSWORD` | 앱이 쓸 `tour` 계정 비밀번호 |
| `MYSQL_ROOT_PASSWORD` | 컨테이너 MySQL root 비밀번호 (위와 다르게) |
| `KOR_SERVICE_KEY` | 공공데이터포털 Encoding 키 |
| `CORS_ALLOWED_ORIGINS` | 프론트 배포 주소 |

> **`DB_HOST=db` 를 바꾸지 마세요.** `db` 는 compose 서비스 이름이고, 같은 네트워크 안에서
> 이 이름으로 DNS가 잡힙니다. `localhost` 로 두면 앱 컨테이너가 자기 자신에게 붙으려다 실패합니다.
>
> 비밀번호에 **백슬래시(`\`)를 쓰지 마세요.** 이 파일은 properties 로 읽힙니다.

---

## 1. 로컬에서 먼저 띄워 본다

서버에 올리기 전에 내 PC에서 똑같은 구성으로 돌려 봅니다. 여기서 안 되면 서버에서도 안 됩니다.

> **이 구성은 2026-09-19 로컬에서 실제로 검증했습니다.** 이미지 빌드(컨테이너 안 Gradle 빌드 35초),
> DB healthcheck 대기 후 앱 기동, `/v3/api-docs` 200, `region_code` 18행,
> 컨테이너 시간대 KST, 비root(uid 999) 실행까지 확인했습니다.
> 이미지 크기는 앱 **581MB**, `mysql:8.0` **1.1GB** 입니다(서버 디스크 여유 2GB 이상 필요).

```bash
docker compose --env-file .env.docker up -d --build
```

처음에는 Gradle 의존성을 받느라 몇 분 걸립니다. 상태를 봅니다.

```bash
docker compose --env-file .env.docker ps
```

`tour-db` 가 `healthy`, `tour-app` 이 `running` 이어야 합니다.
앱은 DB가 healthy가 될 때까지 기다렸다 시작합니다.

```bash
docker compose --env-file .env.docker logs -f app
```

확인합니다.

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/v3/api-docs
```

`200` 이면 성공입니다. 브라우저로 <http://localhost:8080/swagger-ui.html> 도 열어 봅니다.

시드 데이터도 확인합니다. 강원 18개 시·군이 `data.sql` 로 들어갑니다.

```bash
docker compose --env-file .env.docker exec db \
  mysql -u tour -p tour -e "SELECT COUNT(*) AS region_count FROM region_code;"
```

`18` 이 나와야 합니다.

정리는:

```bash
docker compose --env-file .env.docker down
```

DB 데이터까지 지우려면 `down -v` 입니다. 볼륨(`tour-db-data`)이 날아갑니다.

---

## 2. Docker Hub 로그인과 푸시

**2-1. 저장소 준비**

<https://hub.docker.com> 에서 계정을 만들고 로그인합니다.
공개(public) 저장소면 미리 만들지 않아도 첫 push 때 자동 생성됩니다.

> 이미지는 공개됩니다. **`.dockerignore` 가 `.env` 를 제외하므로 키는 이미지에 들어가지 않습니다.**
> 키는 서버의 `.env.docker` 로만 주입됩니다. 비공개로 두고 싶으면 Docker Hub에서
> 저장소를 private으로 먼저 만드세요(무료 계정은 private 1개).

**2-2. 로그인**

```bash
docker login -u <도커허브_아이디>
```

비밀번호 대신 **Access Token** 을 쓰는 걸 권합니다.
Docker Hub > `Account Settings > Personal access tokens > Generate new token` 에서
`Read & Write` 권한으로 만들고, 그 값을 비밀번호 자리에 붙여 넣습니다.

**2-3. 이미지 빌드와 푸시**

compose가 `.env.docker` 의 `DOCKERHUB_USERNAME` 과 `TAG` 로 태그를 붙입니다.

```bash
docker compose --env-file .env.docker build app
```

```bash
docker compose --env-file .env.docker push app
```

만든 태그를 확인하려면 (셸 무관):

```bash
docker images --filter "reference=*/tour-backend"
```

푸시가 실제로 올라갔는지는 Docker Hub에서 확인합니다.

```bash
curl.exe -s "https://hub.docker.com/v2/repositories/<아이디>/tour-backend/tags/"
```

`"name":"latest"` 가 보이면 성공입니다. 브라우저로 `https://hub.docker.com/r/<아이디>/tour-backend` 를 열어도 됩니다.

> **Apple Silicon(M1~)에서 빌드한다면** 서버가 x86_64라 그대로 돌지 않습니다.
> `docker build --platform linux/amd64 -t <아이디>/tour-backend:latest .` 로 따로 빌드해 푸시하세요.
> Windows·Intel Mac은 그대로 하면 됩니다.

버전을 남기고 싶으면 `.env.docker` 의 `TAG` 를 `v1`, `v2` 처럼 올려 가며 푸시합니다.
`latest` 만 쓰면 어떤 이미지가 떠 있는지 나중에 구분할 수 없습니다.

---

## 3. NCP 서버 준비

> ⚠️ **NCP 콘솔의 화면 클릭 절차(3-1~3-3)는 직접 확인하지 못했습니다(미확인).**
> 콘솔 UI는 바뀔 수 있으니 하단 공식 문서 링크와 함께 보세요.
> **3-4 접속 방법(`root` 계정, 비밀번호 인증)은 공식 가이드로 확인했습니다.**

**3-1. VPC와 Subnet**

`Services > Networking > VPC` 에서 VPC 생성(예: `10.0.0.0/16`),
`Subnet` 에서 Subnet 생성(예: `10.0.1.0/24`).

**Internet Gateway 전용 여부를 `Y (public)`** 로 둡니다. Private Subnet이면 공인 IP를 붙일 수 없고,
VPC 환경은 Classic과 달리 포트 포워딩으로 접속할 수 없습니다.

**3-2. 서버 생성**

`Compute > Server > 서버 생성`

| 항목 | 선택 |
| --- | --- |
| 서버 이미지 | Ubuntu Server 22.04 또는 24.04 LTS |
| VPC / Subnet | 위에서 만든 **Public Subnet** |
| 스펙 | **2vCPU / 4GB 이상** — 컨테이너 2개(MySQL 포함)라 2GB는 빡빡합니다 |
| 공인 IP | 새로 할당 |
| 인증키 | 새로 생성 후 `.pem` 파일 보관 |

> `.pem` 은 SSH 키가 아니라 **콘솔에서 관리자 비밀번호를 확인할 때 올리는 파일**입니다(AWS와 다릅니다).
> 잃어버리면 서버를 정지시키고 인증키를 변경해야 합니다.

**3-3. ACG (방화벽)**

`Server > ACG > ACG 설정 > 인바운드 규칙 추가`

| 프로토콜 | 접근 소스 | 허용 포트 | 용도 |
| --- | --- | --- | --- |
| TCP | `내공인IP/32` | 22 | SSH |
| TCP | `0.0.0.0/0` | 8080 | API 공개 |

내 공인 IP는 <https://ifconfig.me> 에서 확인합니다.

> **3306은 열지 않습니다.** DB 컨테이너는 호스트에 포트를 내보내지 않고,
> 앱 컨테이너와 도커 내부 네트워크로만 통신합니다.

**3-4. 접속**

`Server > 서버 선택 > 서버 관리 및 설정 변경 > 관리자 비밀번호 확인` 에서 `.pem` 을 올려
비밀번호를 확인합니다.

```bash
ssh root@<공인IP>
```

> ⚠️ **계정은 `root` 입니다. `ubuntu` 가 아닙니다.** AWS 습관대로 `ubuntu` 를 쓰면
> 비밀번호가 맞아도 `Permission denied` 가 반복됩니다.
>
> ⚠️ **`-i xxx.pem` 을 붙이지 마세요.** NCP 의 `.pem` 은 SSH 키가 아니라 콘솔에서
> 비밀번호를 확인할 때만 쓰는 파일입니다. 접속은 **비밀번호 인증**으로 합니다.
>
> 근거: [Server 접속 (NCP 공식 가이드)](https://guide.ncloud-docs.com/docs/server-access-vpc) —
> "login as가 표시되면 **root**를 입력한 후 \[Enter\] 키를 눌러 주십시오."

들어가서 비밀번호를 바꿉니다: `passwd`

`root` 로 접속하므로 이후 명령의 `sudo` 는 붙이든 안 붙이든 동작합니다.

---

## 4. 서버에 Docker 설치

```bash
sudo apt update && sudo apt upgrade -y
sudo timedatectl set-timezone Asia/Seoul
```

> 타임존을 맞추는 이유: 날짜 탐색(`dateMode`)이 `LocalDate.now()` 로 지원 범위(오늘~30일)를
> 계산합니다. 컨테이너에도 `TZ=Asia/Seoul` 을 넣었지만 호스트도 맞춰 두는 게 로그 해석에 편합니다.

공식 설치 스크립트를 씁니다.

```bash
curl -fsSL https://get.docker.com | sudo sh
```

sudo 없이 쓰려면 그룹에 넣고 **다시 로그인**합니다.

```bash
sudo usermod -aG docker $USER
```

확인:

```bash
docker version && docker compose version
```

> 수동 설치 절차는 <https://docs.docker.com/engine/install/ubuntu/> 에 있습니다.

---

## 5. 서버에 올리고 실행

**5-1. 파일 2개만 올립니다.** 소스도, jar도 필요 없습니다.

**내 PC에서:**

```bash
scp docker-compose.prod.yml .env.docker root@<공인IP>:~/
```

> `.env.docker` 에는 API 키와 DB 비밀번호가 들어 있습니다. 서버에 올린 뒤 권한을 잠급니다.

**5-2. 서버에서:**

```bash
chmod 600 ~/.env.docker
```

private 저장소를 쓴다면 서버에서도 로그인합니다(public이면 건너뜁니다).

```bash
docker login -u <도커허브_아이디>
```

받아서 띄웁니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker pull
docker compose -f docker-compose.prod.yml --env-file .env.docker up -d
```

**5-3. 확인**

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker ps
docker compose -f docker-compose.prod.yml --env-file .env.docker logs -f app
```

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/v3/api-docs
```

내 PC 브라우저에서:

- API 문서 — `http://<공인IP>:8080/swagger-ui.html`
- 관광지 목록 — `http://<공인IP>:8080/api/v1/attractions?sigunguCode=1&size=5`

---

## 6. 재배포

코드를 고친 뒤:

```bash
# 내 PC
docker compose --env-file .env.docker build app
docker compose --env-file .env.docker push app
```

```bash
# 서버
docker compose -f docker-compose.prod.yml --env-file .env.docker pull
docker compose -f docker-compose.prod.yml --env-file .env.docker up -d
```

`up -d` 는 이미지가 바뀐 컨테이너만 다시 만듭니다. DB 컨테이너와 볼륨은 그대로 남습니다.

오래된 이미지가 쌓이면:

```bash
docker image prune -f
```

---

## 7. 알아 둘 것

**DB 데이터는 볼륨에 있습니다.** `tour-db-data` 라는 이름 있는 볼륨입니다.
`docker compose down` 으로는 안 지워지고, `down -v` 를 해야 지워집니다.
**운영 서버에서 `-v` 를 붙이지 마세요.**

**`data.sql` 은 매 기동마다 실행됩니다.** `ON DUPLICATE KEY UPDATE` 라서 중복 적재되지 않습니다.

**온라인 언급량·TMAP 순위는 수집·적재를 따로 돌려야 채워집니다.** 기본은 자동 실행되지 않습니다.
비어 있으면 `sort=ONLINE_MENTION_DESC` 가 공급자 순서를 그대로 쓰고
각 항목의 `onlineMention.status` 가 `COLLECTION_FAILED` 로 내려갑니다.
(`MentionStatus` 의 값은 `COLLECTED` · `AMBIGUOUS` · `UNAVAILABLE` · `COLLECTION_FAILED` 네 가지입니다.
활성 스냅샷에 그 관광지가 없을 때 `OnlineMentionView.notCollected()` 가 `COLLECTION_FAILED` 를 돌려줍니다.
언급이 적다는 뜻이 아니라 **값을 얻지 못했다**는 뜻이라, `0` 으로 채우지 않습니다.)

**주차장 표준데이터도 적재해야 채워집니다.** 돌리지 않으면 상세 응답의 주차 상태가
강릉시 13곳(실시간 공급자) 밖에서 전부 `NO_DATA` 로 내려갑니다. "주차장이 없다"(`NONE`)
가 아니라 **"있는지 없는지 확인하지 못했다"** 는 뜻입니다.

언급량 수집은 **월 1회 스케줄로 돌릴 수 있습니다.** 파일로 받는 데이터(주차장·TMAP·입장객)는
사람이 내려받아야 해서 스케줄이 없습니다. 다음 절을 보세요.

**컨테이너 안에서 DB 를 직접 보려면:**

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker exec db mysql -u tour -p tour
```

---

## 8. 적재·수집을 서버에서 돌리기

### 8-1. 순서대로 손으로 한 번 돌린다

스케줄을 켜기 전에 **반드시 손으로 먼저 돌립니다.** 실제 소요 시간과 외부 API 호출량을
모르는 채로 주기 실행을 켜면, 사람이 보지 않는 새벽에 처음으로 돌게 됩니다.

`run --rm` 은 같은 이미지로 일회성 컨테이너를 띄워 작업만 하고 지웁니다.
`--no-deps` 를 붙이지 않으므로 DB 컨테이너가 떠 있어야 합니다.

**순서가 있습니다.**

```
카탈로그  →  파일 적재(주차장·TMAP·입장객)  →  장소 매핑  →  대표 사진 보강  →  언급량
```

장소 매핑은 TMAP·입장객 적재가 남긴 미매칭 이름을 보므로 그 뒤에 옵니다. 언급량과는
서로를 보지 않으니 앞이든 뒤든 상관없습니다.

카탈로그가 맨 앞인 이유는 나머지가 그것을 딛고 서기 때문입니다. 언급량 수집은 카탈로그를
순회하고, TMAP·입장객은 카탈로그의 `contentId` 에 매칭됩니다. 카탈로그가 비어 있으면
수집이 멈추거나 매칭이 0건이 됩니다.

**대표 사진 보강도 카탈로그를 딛고 섭니다** — 카탈로그에서 사진이 빈 관광지를 대상으로
삼기 때문입니다. 언급량보다 앞에 두는 이유는 **둘이 같은 네이버 키의 하루 한도(25,000회)를
나눠 쓰기** 때문입니다. 사진 보강이 먼저 끝나 몇 회를 썼는지 보고 나서 언급량을 돌리는 쪽이,
언급량을 한참 돌리다 한도에 걸려 사진이 비는 것보다 낫습니다.

**주차장 적재만은 카탈로그와 무관합니다.** 주차장은 관광지 이름이 아니라 좌표로 잇기
때문에 언제 돌려도 됩니다. 그래도 한 번에 돌릴 때는 같은 자리에 둡니다.

```bash
# 1. 카탈로그 먼저.
docker compose -f docker-compose.prod.yml --env-file .env.docker \
  run --rm app --job=catalog
```

**2. 그다음 파일 적재입니다.** 주차장·TMAP·입장객은 API 가 아니라 사람이 내려받은 파일로
적재하고, 그 파일을 컨테이너에 넣어 줘야 해서 명령 모양이 다릅니다. **8-2** 를 보세요.

```bash
# 3. 그다음 장소 매핑. 원천 이름을 카카오로 찾아 카탈로그에 잇습니다. 8-2-4 를 보세요.
docker compose -f docker-compose.prod.yml --env-file .env.docker \
  run --rm app --job=place-mapping
```

```bash
# 4. 그다음 대표 사진 보강. 공급자 사진이 빈 관광지를 네이버 이미지 검색으로 메웁니다.
#    네이버 콘솔에서 이미지 API 를 켜 두어야 합니다. 8-2-5 를 보세요.
docker compose -f docker-compose.prod.yml --env-file .env.docker \
  run --rm app --job=place-image
```

```bash
# 5. 마지막이 언급량. 카탈로그 수천 곳을 도므로 오래 걸립니다.
docker compose -f docker-compose.prod.yml --env-file .env.docker \
  run --rm app --job=mention
```

> **작업이 끝나면 컨테이너가 스스로 종료됩니다.** `--job` 으로 띄운 프로세스는 웹 서버를
> 열지 않고, 작업을 마치면 종료 코드와 함께 내려갑니다. `--rm` 이라 컨테이너도 함께 지워집니다.
> `Ctrl+C` 로 빠져나올 필요가 없고, 명령이 돌아오지 않으면 작업이 아직 도는 중입니다
> (언급량 수집은 카탈로그 수천 곳을 돌아 오래 걸립니다).

| 종료 코드 | 뜻 | 할 일 |
| --- | --- | --- |
| `0` | 작업 성공 | 다음 단계로 |
| `1` | 작업 실패 | `logs` 로 원인을 보고 같은 명령을 다시 돌립니다 |
| `2` | 알 수 없는 작업 이름 | 오타입니다. 명령을 고쳐야 하며 다시 돌려도 같은 답입니다 |

방금 돌린 명령의 결과는 이렇게 봅니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker \
  run --rm app --job=catalog
echo "종료 코드: $?"
```

이어 붙여 돌릴 때는 종료 코드로 끊습니다. 카탈로그가 실패했는데 언급량 수집이 이어지면
빈 카탈로그를 순회하게 됩니다.

```bash
docker compose ... run --rm app --job=catalog && docker compose ... run --rm app --job=mention
```

`--job` 을 주지 않으면 어떤 작업도 실행되지 않습니다. 운영 중인 `tour-app` 컨테이너는
평소대로 서버로만 돕니다 — 그쪽은 `--job` 이 없으므로 예전 그대로 웹 서버입니다.

### 8-2. 파일로 받는 데이터를 적재한다 (주차장·TMAP·입장객)

세 가지는 공급자가 API 를 열어 주지 않아 **사람이 내려받은 파일**로 적재합니다.
어디서 어떤 조건으로 받는지는 `sample/README.md` 에 있습니다.

| 작업 | 원본 | 인자 |
| --- | --- | --- |
| `parking-catalog` | 전국주차장정보표준데이터 CSV 1개 | `--file` 필수 |
| `tmap` | 데이터랩 TMAP 검색순위 zip **18개**(강원 시·군마다 하나) | `--dir` 필수 |
| `visitor-stats` | 주요관광지점 입장객통계 xls 1개 | `--file` 필수 |

**8-2-1. 원본 파일을 서버에 올린다**

> **앱 이미지 안에는 jar 하나뿐이고, compose 에 볼륨 마운트가 없습니다.** 서버에 올려 둔
> 파일은 컨테이너 안에서 그냥 보이지 않습니다. 그래서 파일 적재를 돌릴 때만
> `run --rm -v` 로 디렉터리 하나를 읽기 전용으로 붙입니다. **`docker-compose.prod.yml` 은
> 고치지 않습니다** — 평소 떠 있는 `tour-app` 이 쓰지도 않을 디렉터리를 계속 붙들고 있을
> 이유가 없습니다.

서버에 둘 자리를 만듭니다.

```bash
mkdir -p ~/data/tmap
```

내 PC의 저장소 폴더에서 올립니다(파일명은 예시입니다).

```bash
scp sample/전국주차장정보표준데이터.csv root@<공인IP>:~/data/
scp "sample/주요관광지점 입장객(2004.07 이후)_260918083154.xls" root@<공인IP>:~/data/
scp sample/*.zip root@<공인IP>:~/data/tmap/
```

> **TMAP zip 은 이름을 바꾸지 마세요.** 어느 시·군의 몇 월 데이터인지는 zip 안의 CSV 가
> 아니라 **파일명에만** 있습니다.
> `{타임스탬프}_{시도}+{시군}_{시작월}-{종료월}_데이터랩_다운로드.zip` 형식에서 벗어나면
> 적재가 파일명 검증에서 멈춥니다. 압축도 풀지 마세요.
> 주차장 CSV 와 입장객 xls 는 `--file` 로 직접 지목하므로 이름을 바꿔도 됩니다.

앱 컨테이너는 **비root(`tour`) 로 돕니다.** 올린 파일이 읽히도록 권한을 맞춥니다.

```bash
chmod 755 ~/data ~/data/tmap
chmod 644 ~/data/*.csv ~/data/*.xls ~/data/tmap/*.zip
```

**8-2-2. 적재 명령**

세 명령 모두 `-v "$HOME/data:/data:ro"` 로 그 디렉터리를 컨테이너의 `/data` 에 읽기 전용
으로 붙입니다. **`-v` 는 서비스 이름(`app`) 앞에 와야 합니다.** 뒤에 쓰면 docker 옵션이
아니라 앱에 넘어가는 인자가 되어, 마운트 없이 작업만 돌다 파일을 못 찾고 끝납니다.

주차장 — 강릉시 13곳 밖의 주차 상태가 `NO_DATA` 로 내려가는 것을 막는 적재입니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker \
  run --rm -v "$HOME/data:/data:ro" app \
  --job=parking-catalog --file=/data/전국주차장정보표준데이터.csv --downloaded-on=2026-09-19
```

```
주차장 표준데이터 적재 완료: version=..., 기준일=2026-08-31, 행=1398, 좌표없음=..., 시·군=18
```

전국 파일에서 **제공기관명이 `강원특별자치도` 로 시작하는 행만** 적재합니다.
`시·군` 이 18 이 아니면 파일이 잘렸거나 받는 조건이 전국 전체가 아니었던 겁니다.
`--downloaded-on` 은 파일을 내려받은 날이고, 비우면 오늘로 봅니다. 데이터의 실제 기준일은
CSV 안의 `데이터기준일자` 에서 읽으므로 이 값과 별개입니다.

TMAP — `--file` 이 아니라 zip 이 든 **디렉터리**를 줍니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker \
  run --rm -v "$HOME/data:/data:ro" app \
  --job=tmap --dir=/data/tmap --downloaded-on=2026-09-06
```

```
TMAP 적재 완료: version=..., 지역=18, 행=..., 매칭=...
```

**강원 18개 시·군이 모두 있어야 적재됩니다.** 하나가 빠지면 그 시·군의 순위가 통째로
사라지는데, 그것이 "순위에 들지 못했다" 와 구분되지 않습니다. 그래서 일부만 적재하지 않고
멈춥니다. 18개의 원천 조회기간도 서로 같아야 합니다 — 한 번에 받으세요.

입장객 — 파일명에 공백이 있으므로 `--file=` 부터 끝까지 따옴표로 감쌉니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker \
  run --rm -v "$HOME/data:/data:ro" app \
  --job=visitor-stats \
  --file="/data/주요관광지점 입장객(2004.07 이후)_260918083154.xls" --downloaded-on=2026-09-18
```

```
입장객통계 적재 완료: version=..., 공표월=2026-06, 행=..., 매칭=...
```

공표월은 인자로 주지 않습니다. 파일에는 아직 공표되지 않은 달의 열이 비어 있고 잠정 집계
중인 달은 일부 시·군만 차 있어서, **모든 시·군에 값이 있는 마지막 월**을 적재가 직접
고릅니다. 엑셀을 받을 때 **셀 병합을 해제**해야 합니다(`sample/README.md`).

세 작업 모두 8-1 과 마찬가지로 끝나면 컨테이너가 스스로 종료됩니다. 결과는 종료 코드로
확인합니다(8-1 의 표).

**8-2-3. 언제 다시 받아 적재하나**

| 데이터 | 받는 곳 | 확인 주기 |
| --- | --- | --- |
| 전국주차장정보표준데이터 CSV | data.go.kr 표준데이터 15012896 | **반기(6개월)**. 파일 안 `데이터기준일자` 가 지난번보다 새로우면 적재합니다 |
| TMAP 검색순위 zip 18개 | 한국관광 데이터랩 | **매월 6일 이후** 전월분이 올라왔는지 봅니다 |
| 주요관광지점 입장객통계 xls | 관광지식정보시스템 | **매월 6일 이후** 전월분이 올라왔는지 봅니다 |

입장객통계는 **공표가 분기 단위**라 매달 봐도 새 달이 늘어나 있지 않은 경우가 많습니다.
공표월이 지난번과 같으면 적재하지 않아도 됩니다. 확정치는 이듬해 4월에 공표됩니다.

다시 적재해도 예전 것을 지우지 않습니다. 새 스냅샷이 `ACTIVE` 가 되고 이전 것은
`SUPERSEDED` 로 남습니다. **적재가 실패하면 직전 활성 스냅샷이 그대로 활성으로 남고**
실패한 쪽만 `FAILED` 이력이 됩니다. 조회는 그동안 예전 값을 계속 씁니다.

**이 세 작업은 스케줄로 돌릴 수 없습니다.** 사람이 파일을 내려받아 서버에 올려야 하기
때문입니다. 스케줄이 있는 작업은 언급량과 카탈로그뿐입니다(8-3).

**8-2-4. 장소 매핑을 돌린다**

파일 적재가 끝나면 장소 매핑을 한 번 돌립니다. 원본 파일이 필요 없어 `-v` 마운트도
없습니다. `.env.docker` 에 `KAKAO_REST_API_KEY` 가 있어야 하며, 비어 있으면 호출을 한
번도 내지 않고 이유를 남기고 끝냅니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker \
  run --rm app --job=place-mapping
```

```
장소 매핑 결과: source=tmap, 대상=283, 건너뜀=0, 호출=283, 확정=97(표기차이=34),
저신뢰=103, 미매칭=83, 카탈로그밖=6, 미상=178
장소 매핑 매칭률: source=tmap, 매칭률 제외 전 354/540 (65.6%), 제외 후 354/534 (66.3%),
분모에서 뺀 행=6
```

`--source` 를 주지 않으면 `tmap` → `visitor-stats` → `related` 를 차례로 돕니다. 강원
전체가 카카오 호출 약 655회로, 하루 한도 100,000회의 0.7% 입니다. 확정한 이름은 다음
실행에서 건너뜁니다.

> **확정이 나오면 TMAP·입장객을 다시 적재해야 반영됩니다.** 이 배치는 판정을 표에 남길
> 뿐 스냅샷을 고치지 않습니다. 재적재를 잊는 것이 조용한 실패라서, 배치가 확정 건수와
> 함께 다시 돌릴 명령을 로그에 적습니다. 연관 장소(`related`)는 조회 시점에 표를 보므로
> 재적재가 필요 없습니다.

확정이 나왔다면 **8-2-2** 의 TMAP·입장객 적재를 한 번 더 돌립니다.

매칭률은 **제외 전과 후를 함께** 봅니다. 제외 후만 보면 실제로 더 이어서 오른 것인지
분모를 줄여서 오른 것인지 구별할 수 없습니다. 분류 규칙은 `README.md` 의
**미매칭 재분류** 절에 있습니다.

**8-2-5. 대표 사진을 보강한다**

공급자(KorService2)가 사진을 주지 않은 관광지가 있어 목록 카드와 상세에 빈 자리가 남습니다.
이 배치가 그 자리를 **네이버 이미지 검색**으로 찾은 사진으로 메웁니다(#99, #100).
**돌리지 않으면 오류 없이 사진만 계속 비어 있습니다** — 그래서 잊기 쉬운 단계입니다.

원본 파일이 필요 없어 `-v` 마운트도 없습니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker \
  run --rm app --job=place-image
```

> **전제: 네이버 콘솔에서 이미지 검색 API 가 켜져 있어야 합니다.**
> `.env.docker` 의 `NAVER_API_HUB_KEY_ID`·`NAVER_API_HUB_KEY` 는 언급량 수집(블로그 검색)과
> **같은 키**지만, NAVER API HUB 는 **Application 마다 쓸 API 를 따로 켭니다.** 언급량이 잘
> 돌았다고 해서 이미지 검색도 열려 있는 것이 아닙니다.
>
> 콘솔에서 `전체 서비스 > Application Services > NAVER API HUB > Application` 으로 가서
> 쓰는 Application 의 이용 API 에 **`NAVER 검색 > 이미지`** 를 더하세요.
> 켜지 않고 돌리면 첫 호출에서 아래 401 을 받고 **즉시 중단**합니다(남은 관광지를 계속
> 불러도 모두 같은 답이라서입니다).
>
> ```json
> {"error":{"errorCode":401,"message":"요청한 API는 이 Application에서 활성화되어 있지 않습니다."}}
> ```
>
> 키 자체가 틀렸을 때 오는 401 은 본문이 다릅니다(`errorCode 200`, `Authentication Failed`).
> 둘을 구분해서 보세요 — 고칠 자리가 콘솔이냐 `.env.docker` 냐가 갈립니다.

끝나면 이런 줄이 남습니다. **2026-09-21 로컬에서 실제로 돌린 결과입니다.**

```
작업을 시작합니다: place-image (사진 없는 관광지 대표 사진 보강)
...
대표 사진 보강 완료: 대상=1183, 건너뜀=0, 호출=1183, 보강=1131, 결과없음=52, 실패=0
대표 사진 보강 결과: 대상=1183, 건너뜀=0, 호출=1183, 보강=1131, 결과없음=52, 실패=0
```

**1,183곳에 약 5분** 걸렸습니다(13:18:24 → 13:23:03). 언급량 수집과 달리 한나절 잡고 있을
작업이 아닙니다.

| 숫자 | 뜻 |
| --- | --- |
| `대상` | 카탈로그에서 `image_url` 이 비어 있는 관광지 수 |
| `건너뜀` | 전에 이미 물어본 곳. 다시 돌려도 호출하지 않습니다 |
| `보강` | 쓸 수 있는 사진을 찾은 수 (`status=AVAILABLE`) |
| `결과없음` | 물었지만 결과가 0건 (`status=NONE`). **실패가 아닙니다** |
| `실패` | 그 관광지 하나의 호출·해석이 실패 (`status=FAILED`). 0 이 아니면 로그를 보세요 |

**다시 돌려도 안전합니다.** 한 번 물어본 관광지는 결과와 무관하게 건너뜁니다. 묻고 못 찾은
것과 아직 묻지 않은 것을 같게 두면 매달 같은 곳을 다시 물으며 하루 한도를 깎기 때문입니다.
검색어 규칙이나 필터를 바꿔 다른 답을 기대할 때만 `--refresh` 로 다시 돕니다.

```bash
# 강릉시(51150)만 — 처음 돌려 볼 때 범위를 좁혀 확인하기 좋습니다
docker compose -f docker-compose.prod.yml --env-file .env.docker \
  run --rm app --job=place-image --sigungu=51150

# 이미 답을 받아 둔 곳까지 다시 수집 (한도를 크게 씁니다)
docker compose -f docker-compose.prod.yml --env-file .env.docker \
  run --rm app --job=place-image --refresh
```

**하루 한도(25,000회)를 언급량 수집과 나눠 씁니다.** 같은 키라서입니다. 한 실행의 상한이
20,000회(`PLACE_IMAGE_MAX_CALLS`)라 이 배치가 그달 언급량의 몫까지 쓰지는 않지만,
**사진 보강을 언급량보다 먼저** 돌리는 순서를 지키세요(8-1). 상한에 닿으면 멈추고 남은
관광지는 다음 실행이 봅니다.

들어갔는지는 표로 셉니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker exec db \
  mysql -u tour -p tour -e "SELECT status, COUNT(*) FROM place_image GROUP BY status;"
```

API 로 보려면 목록의 `imageSource` 가 `KOR_SERVICE` 와 `NAVER_IMAGE` 로 섞여 나오는지
봅니다. 자세한 응답 규칙과 되돌리는 방법(`TRUNCATE TABLE place_image`)은 `README.md` 의
**대표 사진 보강** 절에 있습니다.

> **이 사진들은 저작권을 확인한 사진이 아닙니다.** 공모전 제출용(비상업)이라는 전제에서만
> 씁니다. 상업적으로 운영하려면 이 배치를 돌리지 말고 직접 확보한 사진으로 바꿔야 합니다.

### 8-3. 월간 스케줄을 켠다

`.env.docker` 에 넣습니다. **기본은 전부 꺼짐이고, 하나도 켜지 않으면 스케줄러 자체가 뜨지 않습니다.**

| 키 | 기본값 | 뜻 |
| --- | --- | --- |
| `BATCH_SCHEDULE_MENTION_ENABLED` | `false` | 온라인 언급량 월간 수집 |
| `BATCH_SCHEDULE_MENTION_CRON` | `0 0 3 1 * *` (매월 1일 03:00) | 위 작업의 cron (Spring 6자리) |
| `BATCH_SCHEDULE_CATALOG_ENABLED` | `false` | 관광지 카탈로그 재적재 |
| `BATCH_SCHEDULE_CATALOG_CRON` | `0 0 2 1 * *` (매월 1일 02:00) | 위 작업의 cron |
| `BATCH_SCHEDULE_ZONE` | `Asia/Seoul` | cron 을 해석할 시간대 |

**권장: `BATCH_SCHEDULE_MENTION_ENABLED=true` 하나만 켭니다.**
카탈로그 재적재는 공급자(KorService2) 장애가 잦고 실패하면 사람이 봐야 하는 작업이라,
분기마다 손으로 돌리는 편이 낫습니다. 둘 다 켠다면 카탈로그가 먼저 끝나도록
cron 순서를 지켜야 합니다(기본값이 두 시간 차이를 둡니다).

값을 비워 두면 위 기본값을 씁니다. 바꿀 때만 채웁니다.

반영하려면 앱 컨테이너를 다시 만듭니다. 설정 파일만 바뀌었으므로 이미지를 다시 받을 필요는 없습니다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker up -d --force-recreate app
```

### 8-4. 켜졌는지 확인한다

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker logs app | grep 스케줄
```

```
온라인 언급량 수집 스케줄을 등록했습니다: cron=0 0 3 1 * *, zone=Asia/Seoul
```

**이 줄이 없으면 켜지지 않은 것입니다.** 값이 `true` 인지, 컨테이너를 다시 만들었는지 봅니다.

### 8-5. 알아 둘 것

- **이미 진행 중인 수집이 있으면 건너뜁니다.** `online_mention_snapshot` 에 `IMPORTING` 행이
  있으면 스케줄이 깨어나도 수집하지 않고 로그에 남깁니다. 손으로 돌린 수집과 겹쳐 같은 키로
  호출이 두 배가 되는 것을 막기 위해서입니다.
  수집이 비정상 종료해 `IMPORTING` 이 남았다면 다음 달 스케줄도 건너뛰므로, 상태를 확인하세요.

  ```bash
  docker compose -f docker-compose.prod.yml --env-file .env.docker exec db \
    mysql -u tour -p tour -e "SELECT id, version, status, started_at FROM online_mention_snapshot ORDER BY started_at DESC LIMIT 5;"
  ```

- **실패해도 서버는 죽지 않습니다.** 수집이 실패하면 직전 활성 스냅샷이 그대로 남고,
  실패한 스냅샷은 `FAILED` 로 이력에만 남습니다. 조회 결과는 이전 달 값을 계속 씁니다.

- **`--job=...` 으로 띄운 일회성 컨테이너에서는 스케줄러도 웹 서버도 뜨지 않습니다.**
  작업 하나를 돌리려고 띄운 프로세스라 스케줄이 낄 자리가 없고, 8080 을 물 이유도 없습니다.
  작업을 마치면 종료 코드와 함께 내려갑니다(8-1 의 표).

- **사진이 안 채워지는 것은 오류로 드러나지 않습니다.** `--job=place-image` 를 돌리지 않으면
  `place_image` 표가 비어 있고 응답은 전과 똑같이 내려갑니다 — 사진 자리만 빕니다. 배포 뒤에
  화면에 사진이 없다면 먼저 **배치를 돌렸는지**, 그다음 **네이버 이미지 API 가 켜져 있는지**
  를 이 순서로 봅니다(8-2-5). `place-image` 에는 스케줄이 없어 늘 손으로 돌립니다.
  카탈로그를 다시 적재해 새 관광지가 들어왔다면 한 번 더 돌리세요 — 새로 들어온 곳은
  "아직 묻지 않은" 상태라 다음 실행이 대상으로 잡습니다.

---

## 막혔을 때

| 증상 | 확인 |
| --- | --- |
| SSH 가 `Permission denied (publickey,password)` | 계정이 `root` 인지. `ubuntu` 로는 비밀번호가 맞아도 실패합니다. `-i xxx.pem` 도 빼세요 |
| `docker push` 가 `no such host: CHANGEME` | `.env.docker` 의 `DOCKERHUB_USERNAME` 이 자리표시자 그대로입니다. 실제 아이디(소문자)로 바꾼 뒤 `build` 부터 다시 |
| `docker build` 에서 gradlew 권한 오류 | Dockerfile이 `sh ./gradlew` 로 실행하므로 실행 권한은 필요 없습니다. 그래도 나면 로그 전문 확인 |
| 앱이 `Communications link failure` 로 죽는다 | `.env.docker` 의 `DB_HOST` 가 `db` 인지. `localhost` 면 이 증상 |
| 앱이 `Access denied for user 'tour'` | `DB_PASSWORD` 와 DB 볼륨이 어긋난 경우. 볼륨은 **최초 생성 시점의 비밀번호를 유지**합니다. 비밀번호를 바꿨다면 `down -v` 후 재생성(데이터 삭제 주의) |
| `tour-db` 가 계속 `starting` | `logs db` 확인. 최초 초기화는 40초까지 정상 |
| 이미지 push 가 `denied` | `docker login` 여부, `DOCKERHUB_USERNAME` 이 실제 아이디와 같은지 |
| 서버에서 pull 이 `not found` | 이미지가 private인데 서버에서 `docker login` 안 함 |
| 브라우저에서 8080 이 안 열린다 | ACG 인바운드 8080, 그리고 `docker compose ps` |
| 날짜 판정이 하루 밀린다 | 컨테이너 안 `date` 가 KST인지. `docker compose exec app date` |
| 컨테이너가 OOM 으로 죽는다 | 서버 메모리. 4GB 미만이면 MySQL+JVM이 빠듯합니다 |
| 스케줄을 켠 뒤 앱이 아예 뜨지 않는다 | cron 형식이나 시간대 이름이 틀리면 기동 자체가 막힙니다. `BATCH_SCHEDULE_*_CRON` 은 Spring cron **6자리**(`0 0 3 1 * *`), `BATCH_SCHEDULE_ZONE` 은 IANA 시간대 이름(`Asia/Seoul`, `KST` 아님)이어야 합니다. `logs app` 첫 화면에서 확인하세요 |
| 스케줄을 켰는데 기동 로그에 등록 줄이 없다 | `.env.docker` 의 `BATCH_SCHEDULE_..._ENABLED` 가 `true` 인지. 설정 파일만 고쳤다면 `up -d --force-recreate app` 으로 컨테이너를 다시 만들어야 합니다 |
| 스케줄이 돌 시각이 지났는데 수집이 없다 | `online_mention_snapshot` 에 `IMPORTING` 이 남아 있으면 건너뜁니다(8-5). 컨테이너 시간대도 확인: `exec app date` |
| `run --rm app --job=...` 이 작업 없이 서버만 뜬다 | 이미지가 옛 버전입니다. 인자를 앱에 넘기는 `ENTRYPOINT` 수정이 들어간 이미지로 다시 빌드·푸시하세요 |
| 작업이 끝났는데 컨테이너가 내려가지 않는다 | 이미지가 옛 버전입니다(작업 후 종료는 #95 부터). 그 사이에는 `Ctrl+C` 로 빠져나옵니다 |
| `--job=...` 이 `Port 8080 is already in use` 로 죽는다 | 같은 이유로 옛 이미지입니다. 지금은 배치 프로세스가 웹 서버를 열지 않습니다 |
| 파일 적재가 `파일이 아닙니다: /data/...` 로 실패 | `-v` 를 서비스 이름 뒤에 썼을 가능성이 큽니다. `run --rm -v "$HOME/data:/data:ro" app --job=...` 순서를 지키세요. 컨테이너 안에서 실제로 보이는지 확인: `run --rm -v "$HOME/data:/data:ro" --entrypoint ls app -l /data` |
| 파일 적재가 `Permission denied` 로 실패 | 앱 컨테이너는 비root(`tour`) 로 돕니다. `chmod 755 ~/data ~/data/tmap` 과 `chmod 644 ~/data/*.csv ~/data/*.xls ~/data/tmap/*.zip` (8-2-1) |
| `--job=tmap` 이 `데이터랩 다운로드 파일명 형식이 아닙니다` | zip 이름을 바꿨거나 압축을 풀었습니다. 어느 시·군인지는 파일명에만 있습니다. 데이터랩에서 받은 이름 그대로 두세요 |
| `--job=tmap` 이 지역이 모자라다며 멈춘다 | 강원 18개 시·군 zip 이 모두 있어야 하고 원천 조회기간도 같아야 합니다(8-2-2). `ls ~/data/tmap/*.zip \| wc -l` 이 18 인지 |
| 적재는 성공했는데 주차 상태가 여전히 `NO_DATA` | `--job=parking-catalog` 를 돌렸는지, 로그의 `시·군` 이 18 인지 봅니다. 좌표(`위도`·`경도`)가 빈 행은 적재돼도 반경 조회에서 빠집니다 |
| 강릉인데 실시간 주차가 안 나온다 | `KOR_SERVICE_KEY` 는 맞는데 **강릉시 실시간 주차(`GNitsTrafficInfoService_1.0`) 활용신청을 따로 안 한 경우**입니다. 같은 키라도 서비스마다 신청이 필요하고, 빠지면 오류 없이 상태만 정보 없음이 됩니다 |
| **사진이 안 채워진다** | 둘 중 하나입니다. ① **배치 미실행** — `--job=place-image` 를 돌렸는지 보세요(8-2-5). `SELECT COUNT(*) FROM place_image;` 가 0 이면 한 번도 안 돈 것입니다. ② **네이버 이미지 API 미활성** — 로그에 401 과 `요청한 API는 이 Application에서 활성화되어 있지 않습니다` 가 있으면 콘솔에서 `NAVER 검색 > 이미지` 를 켜야 합니다. 키가 틀렸을 때의 401 은 본문이 `Authentication Failed` 라 구분됩니다 |
| `--job=place-image` 가 곧바로 멈춘다 | 401(인증·미활성)과 429(한도 초과)는 **즉시 중단**합니다. 남은 곳을 계속 불러도 같은 답이라서입니다. 그때까지 채운 사진은 남아 있으니, 원인을 고친 뒤 같은 명령을 다시 돌리면 이어서 갑니다 |
| 사진은 채웠는데 `결과없음` 이 많다 | 실패가 아닙니다. 검색으로 사진을 찾지 못한 관광지입니다(로컬 실측 1,183곳 중 52곳). `--refresh` 로 다시 돌려도 검색어 규칙이 그대로면 같은 답이 옵니다 |

## 참고 문서

- [Docker Engine 설치 (Ubuntu)](https://docs.docker.com/engine/install/ubuntu/)
- [신규 콘솔 화면으로 Server 생성 - VPC](https://guide.ncloud-docs.com/docs/server-create-vpc)
- [ACG](https://guide.ncloud-docs.com/docs/ko/server-acg-vpc)
