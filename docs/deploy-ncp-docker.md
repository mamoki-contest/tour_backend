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

**온라인 언급량·TMAP 순위는 수집·적재를 따로 돌려야 채워집니다.** 자동 실행되지 않습니다.
비어 있으면 `sort=ONLINE_MENTION_DESC` 가 공급자 순서를 그대로 쓰고
각 항목이 `NOT_COLLECTED` 로 내려갑니다.

**컨테이너 안에서 DB 를 직접 보려면:**

```bash
docker compose -f docker-compose.prod.yml --env-file .env.docker exec db mysql -u tour -p tour
```

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

## 참고 문서

- [Docker Engine 설치 (Ubuntu)](https://docs.docker.com/engine/install/ubuntu/)
- [신규 콘솔 화면으로 Server 생성 - VPC](https://guide.ncloud-docs.com/docs/server-create-vpc)
- [ACG](https://guide.ncloud-docs.com/docs/ko/server-acg-vpc)
