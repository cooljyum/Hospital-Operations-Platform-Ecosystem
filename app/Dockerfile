# syntax=docker/dockerfile:1
#
# Spring Boot 멀티스테이지 빌드.
# 1) builder: Gradle Wrapper로 bootJar 생성 (JDK 17)
# 2) runtime: 생성된 jar만 담은 최소 실행 이미지 (JRE 17)
#
# docker/docker-compose.yml의 app 서비스가 build.context=../app, dockerfile=Dockerfile로 참조한다.

FROM eclipse-temurin:17-jdk AS builder

WORKDIR /workspace

# 의존성 캐시 레이어를 소스 복사보다 먼저 굳히기 위해 빌드 스크립트/wrapper부터 복사한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

# 소스 없이 wrapper만 먼저 받아 Gradle 배포본 다운로드를 이 레이어에 캐시한다.
RUN ./gradlew --version --no-daemon

COPY src ./src

# 테스트는 이 빌드 단계에서 생략(단위테스트는 CI/로컬 `./gradlew build`에서 별도 실행).
# bootJar만 실행하므로 build/libs 안에는 실행 가능한 jar 하나만 생성된다(plain jar 미생성).
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre AS runtime

# app 서비스의 docker-compose healthcheck가 컨테이너 내부에서 curl로 자가 점검한다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --no-create-home --shell /usr/sbin/nologin appuser

WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
