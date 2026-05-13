# syntax=docker/dockerfile:1.7
# 파이썬 3.12 데비안(Debian) 베이스 이미지 사용
FROM python:3.12-bookworm

# 2. Amazon Corretto 17 공식 저장소 추가 및 JDK 설치
RUN wget -O - https://apt.corretto.aws/corretto.key | gpg --dearmor -o /usr/share/keyrings/corretto-keyring.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/corretto-keyring.gpg] https://apt.corretto.aws stable main" | tee /etc/apt/sources.list.d/corretto.list \
    && apt-get update \
    && apt-get install -y java-17-amazon-corretto-jdk \
    && rm -rf /var/lib/apt/lists/*

# 3. JAVA_HOME 환경변수 설정 (Gradle 및 Java 앱 실행을 위해 필수)
ENV JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# 4. uv 복사 및 설정
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /usr/local/bin/
ENV PATH="/root/.local/bin:${PATH}"

# 5. aider-chat 설치
# (이미 파이썬-데비안 환경이므로 Rust 없이 미리 빌드된 패키지를 받아 10초 만에 끝납니다!)
RUN curl -LsSf https://aider.chat/install.sh | sh



WORKDIR /app

# 6. 의존성 캐싱
COPY gradlew settings.gradle* build.gradle* ./
COPY gradle/ ./gradle/
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
       ./gradlew --no-daemon dependencies

# 7. 소스 빌드
COPY . .
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

EXPOSE 8090
CMD ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar $(ls build/libs/*.jar | grep -v plain | head -1)"]
