# syntax=docker/dockerfile:1

# ---------- 1단계: 빌드 ----------
# 빌드 전용 단계를 따로 둔다. 최종 이미지에 JDK·Gradle·소스코드를 남기지 않기 위해서다.
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# 래퍼와 빌드 스크립트를 소스보다 먼저 복사한다.
# 소스만 바뀐 재빌드에서 이 레이어가 캐시에 남아 의존성 해석을 건너뛴다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle

COPY src src

# 테스트는 MySQL 이 있어야 돌아가므로 이미지 빌드에서는 제외한다.
# 테스트는 로컬에서 `./gradlew test` 로 돌린다. 여기서 건너뛴다고 검증을 생략하는 것이 아니다.
#
# --mount=type=cache 는 Gradle 캐시를 빌드 간에 재사용한다. 이미지에는 남지 않는다.
# bootJar 만 돌리면 build/libs 에 실행 가능한 jar 하나만 나온다(plain jar 는 생성되지 않는다).
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew bootJar --no-daemon -x test \
 && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)" /workspace/app.jar

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:21-jre

# 날짜 탐색(dateMode)이 서버의 LocalDate.now() 로 지원 범위(오늘~30일)를 계산한다.
# UTC 로 두면 한국 시간 오전 9시 이전에 날짜가 하루 밀려 한산·보통·혼잡 판정이 어긋난다.
ENV TZ=Asia/Seoul

# 컨테이너 메모리의 일부만 힙으로 쓴다. MySQL 과 같은 서버에 올릴 때 서로 밀어내지 않게 한다.
# compose 에서 JAVA_OPTS 로 덮어쓸 수 있다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

WORKDIR /app

# root 로 실행하지 않는다.
RUN useradd --system --create-home --shell /usr/sbin/nologin tour

COPY --from=build --chown=tour:tour /workspace/app.jar app.jar

USER tour

EXPOSE 8080

# TZ 환경변수만으로 JVM 이 시간대를 못 잡는 경우가 있어 -Duser.timezone 으로 한 번 더 못박는다.
#
# 끝의 "$@" 와 "--" 는 컨테이너에 준 인자를 그대로 넘기기 위한 것이다. 이게 없으면
# `docker compose run --rm app --job=catalog` 의 인자가 sh 에게만 가고 앱에는 닿지 않아,
# 적재·수집 작업을 컨테이너로 돌릴 방법이 없다. 인자를 주지 않으면 지금까지와 같다.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Duser.timezone=Asia/Seoul -jar /app/app.jar \"$@\"", "--"]
