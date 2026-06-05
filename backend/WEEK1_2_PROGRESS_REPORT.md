# 🚀 Agent-Harness 백엔드 개발 진행 상황 보고서 (Week 1 ~ Week 2)

**담당자:** 장성욱 (Backend Core Engineer)  
**작성일:** 2026년 05월 13일  
**문서 목적:** Week 1 및 Week 2 백엔드 스프린트 완료 내역 정리 및 스프린트 회의 보고용

---

## ✅ 1. 완료된 작업 내역 (Done)

### 📌 Week 1: 인프라 기반 구축 및 스키마 설계
MVP(최소 기능 제품) 환경에 맞추어 백엔드 핵심 뼈대를 구축했습니다.
- **Spring Boot 프로젝트 초기화:** `Java 17`, `Spring Boot 3.2.x`, `Gradle` 기반으로 프로젝트 세팅 완료 (Web, Data JPA, Lombok 의존성 추가).
- **PostgreSQL 연동:** `application.yml`을 통해 로컬 도커 DB(`agent_harness`)와의 연결 세팅 완료. 포트 충돌 방지를 위해 `5433` 포트를 사용하도록 우회 설정 적용.
- **JPA 엔티티(Entity) 설계:** TADD 자동 테스트 파이프라인 검증을 위한 핵심 스키마 설계.
  - `AgentTask`: AI 작업의 프롬프트 명세와 진행 상태(PENDING, RUNNING 등) 저장.
  - `TaskResult`: 작업 종료 후 단위 테스트 성공 여부(isSuccess), 종료 코드(exitCode), 실행 소요 시간, 토큰 사용량 저장.

### 📌 Week 2: 코어 엔진 PoC (ProcessBuilder 및 I/O 스트림)
AI 에이전트(CLI)를 백그라운드에서 구동하기 위한 핵심 프로세스 제어 엔진을 개발했습니다.
- **`ProcessOrchestratorService` 구현:** 자바의 `ProcessBuilder`를 래핑하여 외부 CLI 명령어를 백그라운드로 실행하고 강제 종료(`stopProcess()`)할 수 있는 기능 완비.
- **실시간 스트림 파싱:** `PYTHONUNBUFFERED=1` 환경 변수를 적용하여 출력 버퍼링을 해제하고, 백그라운드 스레드에서 I/O 스트림(`Stdout`, `Stderr`)을 한 줄씩 `[AGENT LOG]`로 가로채는 로직 구현 (메모리 릭 방지).
- **PoC 컨트롤러 개발:** 프론트엔드 연동 전 단독 테스트를 위한 임시 REST API(`POST /api/v1/agent/run-test`) 구축 및 `ping` 명령어를 통한 터미널 출력 완벽 검증.

---

## 🛠️ 2. 기술적 문제 해결 및 리팩토링 (Tech Highlights)

단순한 기능 구현을 넘어, 시스템의 **안정성(Stability)**과 **동시성(Concurrency)** 문제를 선제적으로 해결했습니다.

1. **스레드 안전성 (Thread-Safety) 확보:**
   - 여러 요청이 동시에 들어왔을 때 프로세스가 중복 실행되는 것을 막기 위해 `AtomicBoolean (isRunning)`의 `compareAndSet` 메서드를 활용했습니다.
   - 이를 통해 MVP 요구사항인 **'단일 에이전트 실행'** 방어 로직을 Race Condition 없이 완벽하게 구축했습니다.
   
2. **비동기 스레드 풀(ExecutorService) 적용 (Anti-pattern 제거):**
   - 기존 컨트롤러에서 무분별하게 `new Thread()`를 생성하던 스프링 안티 패턴을 리팩토링했습니다.
   - 서비스 레이어 내부에 고정된 크기의 스레드 풀(`Executors.newFixedThreadPool`)을 선언하여, 자원을 안전하게 관리하면서도 백그라운드 I/O 블로킹 현상을 완벽히 제거했습니다.

3. **엣지 케이스(Edge Case) 방어 및 예외 처리:**
   - 실행 중인 프로세스를 즉시 죽이는 강제 종료(`kill`) 로직 추가.
   - 존재하지 않는 명령어를 넣었을 때 서버가 뻗지 않고 Stderr를 파싱해 우아하게 종료되도록 에러 핸들링 완료.

---

## 🧪 3. 테스트 방법 (How to Test)

1. **DB 구동:** 
   ```bash
   docker run --name pg-harness-new -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=agent_harness -p 5433:5432 -d postgres:15
   ```
2. **서버 실행:** `OSP_Project/backend` 경로에서 `.\gradlew bootRun`
3. **API 테스트 (Postman 또는 터미널):**
   - **정상 실행:** `POST http://localhost:8090/api/v1/agent/run-test`
   - **강제 종료:** `POST http://localhost:8090/api/v1/agent/stop`
   - **에러 명령어 테스트:** `POST http://localhost:8090/api/v1/agent/run-error-test`

---

## 🎯 4. 다음 주차 목표 (Week 3)

- **SSE (Server-Sent Events) 연동:** 현재 백엔드 콘솔에 출력되고 있는 `[AGENT LOG]` 실시간 스트림을 프론트엔드 대시보드(UI)로 지연 없이 쏴주기 위한 단방향 비동기 통신 채널을 구축합니다.
- **자동 테스트(pytest) 트리거 구현:** ProcessBuilder의 실행이 0(Success)으로 종료되는 이벤트를 감지하면, 격리된 환경에서 즉시 검증용 파이썬 스크립트가 실행되도록 파이프라인을 이어붙일 예정입니다.
