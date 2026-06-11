# ==========================================
# 1단계: Build Stage (Amazon Corretto 17 JDK + Gradle 9.4.1 Wrapper 빌드)
# ==========================================
# JDK 17로 Amazon Corretto 공식 이미지를 사용합니다.
FROM amazoncorretto:17 AS builder

WORKDIR /build

# build context가 repo 루트이므로 backend/ 경로를 명시합니다.
COPY backend/gradlew ./
COPY backend/gradle/ gradle/
COPY backend/build.gradle backend/settings.gradle ./
COPY backend/src ./src

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

# 1. Git 설치 (Debian/Ubuntu 예시)
RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*

RUN pip install pytest

# 2. Aider(AI 비서)가 커밋할 때 사용할 기본 Git 유저 정보 설정
RUN git config --global user.email "aider@myproject.com" && \
    git config --global user.name "Aider Bot"

ENV PYTHONUNBUFFERED=1

WORKDIR /app

# 1단계 builder에서 생성된 jar 파일을 복사합니다.
COPY --from=builder /build/build/libs/*.jar app.jar

# Python 래퍼 스크립트 복사 (ProcessOrchestratorService에서 subprocess로 호출)
COPY backend/run_wrapper.py backend/run_wrapper.py
COPY scripts/ scripts/

# [옵션 A] 빌드된 스프링 부트 애플리케이션을 컨테이너 실행 시 구동하려는 경우
ENTRYPOINT ["java", "-jar", "app.jar"]

# [옵션 B] Aider 환경을 유지한 채 JRE를 연동하여 사용하려는 경우
# 위의 ENTRYPOINT 라인을 주석 처리하거나 삭제하시면 Aider의 원래 Entrypoint가 실행됩니다.
