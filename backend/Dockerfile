# ==========================================
# 1단계: Build Stage (Amazon Corretto 17 JDK + Gradle 9.4.1 Wrapper 빌드)
# ==========================================
# JDK 17로 Amazon Corretto 공식 이미지를 사용합니다.
FROM amazoncorretto:17 AS builder

WORKDIR /build

# 명시해주신 distributionUrl(9.4.1)을 인식할 수 있도록 Gradle Wrapper 관련 파일을 먼저 복사합니다.
COPY gradlew ./
COPY gradle/ gradle/

# Gradle Groovy DSL 설정 파일 복사
COPY build.gradle settings.gradle ./

# 의존성 라이브러리를 먼저 캐싱하기 위해 의존성만 다운로드 시도 (선택 사항이나 캐시 효율에 좋습니다)
# 여기서는 소스 코드 복사 전에 실행되도록 구성할 수 있으나, 간결한 처리를 위해 소스코드와 함께 빌드합니다.
COPY src ./src

# gradlew 실행 권한을 부여하고, Gradle 9.4.1을 사용해 Spring Boot 4.0.6 애플리케이션을 빌드합니다.
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon -x test


# ==========================================
# 2단계: Runner Stage (Aider Core 베이스 + JRE 17 설치)
# ==========================================
# Aider core 공식 이미지를 기반으로 설정합니다.
FROM paulgauthier/aider AS runner

# 패키지 설치를 위해 일시적으로 root 권한으로 전환합니다.
USER root

# Aider 이미지는 Debian 계열이므로 apt-get을 사용해 실행기용 JRE 17을 설치합니다.
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/*

ENV PYTHONUNBUFFERED=1

WORKDIR /app

# 1단계 builder에서 생성된 jar 파일을 복사합니다.
# Gradle 빌드 결과물은 기본적으로 build/libs/ 폴더 아래에 생성됩니다.
COPY --from=builder /build/build/libs/*.jar app.jar

# [옵션 A] 빌드된 스프링 부트 애플리케이션을 컨테이너 실행 시 구동하려는 경우
ENTRYPOINT ["java", "-jar", "app.jar"]

# [옵션 B] Aider 환경을 유지한 채 JRE를 연동하여 사용하려는 경우
# 위의 ENTRYPOINT 라인을 주석 처리하거나 삭제하시면 Aider의 원래 Entrypoint가 실행됩니다.
