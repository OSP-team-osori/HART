# 📌 과제 요구사항 및 시스템 아키텍처 명세

## 1. 기획 배경 및 방향성 (Project Pivot & Strategy)
본 프로젝트는 AI 코딩 에이전트의 산출물을 시스템적으로 검증하는 **'멀티 에이전트 관제 플랫폼'**을 최종 비전으로 삼습니다. 하지만 5주라는 제한된 기한과 멀티 스레딩, Race Condition 등의 기술적 리스크를 고려하여 **[현실적 MVP 전략]**을 채택합니다.
- **선택과 집중:** 단일 핵심 에이전트(Aider, Claude Code 등)의 `[실행 ➔ 로깅 ➔ 자동 테스트 ➔ 평가]` 사이클을 100% 무결점으로 구현하는 데 백엔드 역량을 집중합니다.
- **확장성 시각화:** 대시보드 UI에는 여러 에이전트가 동작할 수 있는 플릿(Fleet) 레이아웃을 구현하되, 메인 에이전트 외에는 세련된 '더미(Dummy)'로 배치하여 향후 확장 가능성을 어필합니다.

---

## 2. 요구사항 명세 (Requirements)
교수님께서 강조하신 TADD(Test + AI Driven Development) 철학을 실제 자동화된 DevOps 인프라로 구현합니다.

### 2.1. 기능적 요구사항
- **AI 프로세스 단일 제어:** Spring Boot 서버는 `ProcessBuilder`를 통해 AI CLI 툴을 자식 프로세스로 실행, 중지, 강제 종료할 수 있어야 합니다.
- **실시간 스트림 파싱:** AI 프로세스의 표준 출력(Stdout/Stderr)을 실시간으로 가로채어 토큰 사용량 및 현재 상태를 정규식으로 추출합니다.
- **TADD 검증 자동화:** 에이전트가 코드 작성을 마치고 정상 종료(Exit Code 0)되면, 즉시 격리된 작업 디렉토리 내에서 단위 테스트(pytest 등)를 자동 실행합니다.
- **평가 지표 DB 적재:** 테스트 종료 후 성공/실패 여부, 소요 시간, 비용(Token) 데이터를 PostgreSQL에 영구 저장하고 에이전트의 '신뢰도 점수'를 갱신합니다.
- **확장형 관제 UI:** SSE(Server-Sent Events)를 통해 메인 에이전트의 터미널 로그를 지연 없이 렌더링하며, 멀티 에이전트 더미 슬롯을 시각적으로 제공합니다.

### 2.2. 비기능적 요구사항
- **단일 컨테이너 격리:** 환경 오염 방지를 위해 Spring 서버, 빌드 도구, AI 도구가 하나의 통합 Docker Image 내에서 실행되어야 합니다.
- **대기 상태 방지 (Hang Prevention):** 사용자 확인(y/n)으로 인한 서버 멈춤을 막기 위해 반드시 Non-interactive 모드(`--yes`)로 강제 실행되어야 합니다.
- **버퍼링 강제 해제:** 실시간 로그 파싱 지연을 없애기 위해 OS 및 도구의 출력 버퍼링을 해제(`PYTHONUNBUFFERED=1`)합니다.

### 2.3. 법적/규제 요구사항
- 본 시스템은 Apache 2.0 라이선스를 따르는 Aider를 핵심 엔진으로 사용하며, 소스코드 도용이 아닌 프로세스 실행(SaaS) 방식을 취해 라이선스를 준수합니다. 배포 시 오픈소스 활용 고지문을 명시합니다.

### 2.4. 보안 및 환경 설정
- MVP 단계에서는 사용자의 초기 진입 장벽을 낮추기 위해, 시스템 관리자가 `.env` 파일에 공용 API 키를 사전 세팅하는 중앙 집중형 방식을 채택합니다.

---

## 3. 시스템 아키텍처 및 기술 스택 (Architecture & Tech Stack)

### 3.1. 인프라 및 아키텍처
- **Unified Docker Runner:** DinD를 피하고 하나의 컨테이너 안에 Java(오케스트레이터)와 Node/Python(작업 환경)을 모두 세팅합니다.
- **Volume Mount:** 호스트 PC 작업 디렉토리를 컨테이너와 동기화하여 코드 변경이 즉시 반영되도록 합니다.
- **Process Orchestrator:** `ProcessBuilder`를 래핑한 싱글톤 서비스가 메인 프로세스 생명주기를 전담 관리합니다.
- **Stream & Event Emitter:** 표준 출력 로그를 줄 단위로 읽고 `SseEmitter`로 프론트엔드에 비동기 푸시합니다.

### 3.2. 기술 스택
- **Backend:** Spring Boot (Java), Spring Data JPA
- **Frontend:** HTML5, TailwindCSS, Vanilla JS (SSE 연동 집중)
- **Database:** PostgreSQL
- **Infra:** Docker, GitHub Actions (CI/CD)
- **Target AI:** Aider (Gemini 1.5 Pro 모델 연동 등)

---

## 4. 시스템 공통 인터페이스 (System Contract)
모든 개발 파트는 아래의 명명 규칙과 인터페이스를 절대적으로 준수해야 합니다.

- **디렉토리 및 언어 표준:** 컨테이너 내부 기준 작업 디렉토리는 `/app/workspace`로 통일하며, `Java 17` 및 `Python 3.10+` 환경을 세팅합니다.
- **Aider 실행 표준:** `aider --model gemini/gemini-1.5-pro --message "[요구사항]" --no-auto-commits --yes`
- **통신 규약 (API & SSE):**
  - `[POST]` 에이전트 작업 지시: `/api/v1/agent/run` (Payload: `{"prompt": "요구사항"}`)
  - `[GET]` 실시간 터미널 스트림 (SSE): `/api/v1/agent/stream` (Response: `data: {"log": "...", "type": "info"}`)
  - `[GET]` 대시보드 최종 결과 조회: `/api/v1/agent/results`

---

## 🛑 [Instruction for AI Assistant]
이 시스템 코드를 작성하는 AI 코딩 어시스턴트는 아래 지침을 엄수해야 합니다.
1. **정체성:** 다수 스레드 관리가 아닌, 단일 에이전트 프로세스 파이프라인 안정성에 100% 집중하는 MVP 백엔드입니다.
2. **동기/비동기 통제:** 멀티 스레드 동시성 이슈를 유발하는 코드를 지양하고, `ProcessBuilder`의 완벽한 순차 실행(Execution ➔ Validation)을 보장하십시오.
3. **실시간 통신:** 무겁고 양방향인 WebSocket 대신, 단방향 스트리밍에 최적화된 Spring `SseEmitter`를 사용하십시오.
4. **I/O 최적화:** 외부 라이브러리 의존도를 낮추고 Java 표준 `InputStream`/`BufferedReader`를 사용한 완벽한 자원 반납(Memory Leak 방지) 로직을 포함하십시오.