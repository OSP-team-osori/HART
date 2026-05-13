# syntax=docker/dockerfile:1.7
FROM amazoncorretto:17

RUN dnf install -y git tar gzip unzip findutils which curl \
    && dnf clean all

# uv + aider
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /usr/local/bin/
ENV PATH="/root/.local/bin:${PATH}"
RUN uv tool install --force --python 3.12 --with pip aider-chat@latest

WORKDIR /app

# 의존성 캐싱
COPY gradlew settings.gradle* build.gradle* ./
COPY gradle/ ./gradle/
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
       ./gradlew --no-daemon dependencies

# 소스 빌드
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

EXPOSE 8090
CMD ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar $(ls build/libs/*.jar | grep -v plain | head -1)"]
