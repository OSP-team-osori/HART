# 🚀 Agent-Harness Backend System

Agent-Harness 백엔드는 **TADD(Test + AI Driven Development) 기반 AI 에이전트 검증 파이프라인**을 제어하고 관리하는 Spring Boot 어플리케이션입니다.  
OS 레벨의 프로세스 제어, 실시간 로그 스트리밍(SSE), 데이터 적재 및 에이전트 신뢰도(Reliability) 평가 엔진을 담당합니다.

---

## 1. 주요 기능 (Key Features)

*   **비동기 프로세스 오케스트레이션:** `java.lang.ProcessBuilder`를 이용해 Aider AI 에이전트 및 검증 단위 테스트(`pytest`)를 백그라운드 프로세스로 관리합니다.
*   **실시간 로그 스트리밍 (SSE):** 에이전트의 터미널 표준 출력(Stdout/Stderr)을 프론트엔드로 끊김 없이 단방향 중계합니다.
*   **TADD 결과 영구 적재:** 작업 성공률, 수행 시간, API 토큰 소비량 등의 평가 지표를 수집하여 DB(PostgreSQL)에 영구 저장합니다.
*   **실시간 신뢰도 산출 엔진:** 축적된 과거 이력과 현재 작업 상태를 유기적으로 가공하여 AI 에이전트의 신뢰도 점수를 실시간 계산합니다.

---

## 2. 프로젝트 패키지 구조 (Backend Architecture)

```text
com.agent.harness/
├── config/             # CORS 설정 등 인프라스트럭처 설정
├── controller/         # REST API & SSE 엔드포인트 제공
│   └── dto/            # API 요청/응답 객체 정의
├── entity/             # JPA Entity 정의 (agent_tasks, task_results)
├── repository/         # Spring Data JPA를 사용한 데이터 액세스 레이어
└── service/            # 프로세스 실행 관리 및 결과 파싱 비즈니스 로직
```

---

## 3. 핵심 API 명세 (Core API Specification)

| HTTP Method | API Endpoint | Description |
| :--- | :--- | :--- |
| **`GET`** | `/api/v1/agent/stream` | 실시간 에이전트 로그를 SSE(Server-Sent Events) 스트림으로 수신 |
| **`POST`** | `/api/v1/agent/run-test` | AI 에이전트 작업 프로세스 비동기 실행 트리거 |
| **`POST`** | `/api/v1/agent/stop` | 실행 중인 에이전트 프로세스 강제 중지 및 리소스 회수 |
| **`GET`** | `/api/v1/agent/latest-result` | 가장 최근에 완료된 작업 결과 통계 및 신뢰도 점수 조회 |

### 최근 결과 조회 API 상세 (`GET /api/v1/agent/latest-result`)
*   **Description:** 초기 로딩 시 대시보드를 채우기 위한 엔드포인트로, 마지막 작업의 디테일과 신뢰도 점수를 반환합니다. 이력이 없는 경우 `404 Not Found`를 반환합니다.
*   **Response 예시:**
    ```json
    {
      "id": 3,
      "taskId": 3,
      "prompt": "Refactor Dockerfile for size reduction",
      "status": "FAILED",
      "createdAt": "2026-06-11T17:55:18.234567",
      "finishedAt": "2026-06-11T17:55:18.235678",
      "isSuccess": false,
      "exitCode": -1,
      "executionTimeMs": 1250,
      "tokensUsed": 450,
      "testStatus": "FAIL",
      "cost": 0.009,
      "testSummary": "Process terminated unexpectedly. Dockerfile syntax error at line 12.",
      "confidenceScore": 32
    }
    ```

---

## 4. 신뢰도 점수 산출 로직 (Reliability Scoring Logic)

에이전트의 **역사적인 테스트 통과율**과 **현재 작업의 완결성**을 균형 있게 다루기 위해 하이브리드 가중 공식을 사용합니다.

$$\text{Confidence Score} = (\text{최근 10개 작업 성공률} \times 0.6) + (\text{현재 작업 상태 점수} \times 0.4)$$

1.  **최근 10개 작업 성공률 (60% 가중치):**
    *   최근 10개의 DB 이력 중 성공(`isSuccess = true`)한 비율을 백분율로 산출합니다. (이력이 없는 경우 100% 기본값 적용)
2.  **현재 작업 상태 점수 (40% 가중치):**
    *   `PASS`: 100점 (테스트 완벽 통과)
    *   `SKIPPED`: 80점 (코드 수정 불필요 등으로 검증 생략)
    *   `FAIL`: 30점 (테스트 실패)
    *   기타 상태: 0점
3.  최종 결과는 첫째 자리에서 반올림한 **0 ~ 100 범위의 정수**로 반환됩니다.

---

## 5. 실행 및 개발 환경 설정 (How to Run)

### 로컬 DB 및 환경 기동
백엔드는 로컬에 기동된 PostgreSQL(기본 포트: 5433)을 필요로 합니다.
```bash
# Docker Compose를 이용한 DB(PostgreSQL) 기동
docker-compose -f infra/docker-compose.yml up -d postgres
```

### 어플리케이션 실행
```bash
# gradlew wrapper를 사용한 빌드 및 로컬 서버 구동
cd backend
.\gradlew.bat bootRun
```
*   백엔드 서버는 `http://localhost:8090` 포트에서 동작합니다.

---

## 6. 테스트 및 검증 (Testing & Verification Guide)

### 1) 자동화 테스트 수행 (Gradle Unit Tests)
백엔드 로직의 정밀 검증을 위해 3가지 조건(기본 성공/실패, SKIPPED 상태 점수 가중합, 최근 10개 초과 필터링)에 대한 통합 테스트가 `AgentHarnessApplicationTests.java`에 반영되어 있습니다.
```bash
cd backend
.\gradlew.bat test
```

### 2) 수동 데이터 주입 검증 (Manual DB Verification)
직접 데이터를 DB에 조작해 넣으면서 API 출력을 확인하고 싶다면 아래 절차를 따릅니다.

1.  **DB 콘솔 진입:**
    ```bash
    docker exec -it postgres psql -U postgres -d agent_harness
    ```
2.  **테스트 데이터 주입 (예시: 시나리오 1 - 성공률 50% + 현재 FAIL):**
    ```sql
    TRUNCATE TABLE task_results CASCADE;
    TRUNCATE TABLE agent_tasks CASCADE;

    INSERT INTO agent_tasks (id, prompt, status) VALUES (1, 'Task 1', 'COMPLETED');
    INSERT INTO task_results (task_id, is_success, test_status) VALUES (1, true, 'PASS');

    INSERT INTO agent_tasks (id, prompt, status) VALUES (2, 'Task 2', 'FAILED');
    INSERT INTO task_results (task_id, is_success, test_status) VALUES (2, false, 'FAIL');
    ```
3.  **DB 콘솔 탈출 (`\q` 입력) 후 API 호출 결과 확인:**
    ```bash
    curl http://localhost:8090/api/v1/agent/latest-result
    ```
    *   응답 JSON 내에서 계산된 `"confidenceScore": 42`를 직접 확인하실 수 있습니다.

### 3) 실시간 Mock 에이전트 구동 및 신뢰도 검증 (Mock Agent Run & Verification)

실제로 백엔드 서버를 기동한 뒤 API 요청을 보내고 로그 수집 스크립트가 로컬에 잘 구동되는지 직접 테스트하려면 아래 절차를 진행합니다.

1.  **백엔드 어플리케이션 및 DB 실행**
    ```cmd
    # 1. PostgreSQL 컨테이너 기동
    docker-compose -f infra/docker-compose.yml up -d postgres

    # 2. 백엔드 구동 (윈도우 CMD)
    cd backend
    gradlew bootRun
    ```

2.  **데이터베이스 초기화 (준비 단계)**
    *   기존 실패/성공 이력을 모두 지우고 최초 상태(100점 시작)부터 깔끔하게 계산 과정을 검증하기 위해 아래 SQL 명령어를 실행하여 데이터를 리셋합니다.
    ```cmd
    docker exec -it postgres psql -U postgres -d agent_harness -c "TRUNCATE TABLE task_results CASCADE; TRUNCATE TABLE agent_tasks CASCADE;"
    ```

3.  **순차 검증 시나리오 진행**

    *   **단계 1: 최초의 작업 통과 (PASS)**
        *   **테스트 실행:**
            ```cmd
            curl -X POST http://localhost:8090/api/v1/agent/run-test -H "Content-Type: application/json" -d "{\"prompt\": \"scripts/dummy_logs/modify_pass.log\"}"
            ```
        *   **점수 계산식:** `(성공률 100% * 0.6) + (PASS 100점 * 0.4) = 60 + 40`
        *   **결과 조회 및 확인:**
            ```cmd
            curl http://localhost:8090/api/v1/agent/latest-result
            ```
            *(🎯 예상 신뢰도 점수: **`100` 점**)*

    *   **단계 2: 두 번째 작업의 테스트 실패 (FAIL)**
        *   **테스트 실행:**
            ```cmd
            curl -X POST http://localhost:8090/api/v1/agent/run-test -H "Content-Type: application/json" -d "{\"prompt\": \"scripts/dummy_logs/modify_fail.log\"}"
            ```
        *   **점수 계산식:** `(성공률 50% * 0.6) + (FAIL 30점 * 0.4) = 30 + 12`
        *   **결과 조회 및 확인:**
            ```cmd
            curl http://localhost:8090/api/v1/agent/latest-result
            ```
            *(🎯 예상 신뢰도 점수: **`42` 점**)*

    *   **단계 3: 세 번째 작업의 변경 없음 (SKIPPED)**
        *   **테스트 실행:**
            ```cmd
            curl -X POST http://localhost:8090/api/v1/agent/run-test -H "Content-Type: application/json" -d "{\"prompt\": \"scripts/dummy_logs/no_modify.log\"}"
            ```
        *   **점수 계산식:** `(성공률 66.67% * 0.6) + (SKIPPED 80점 * 0.4) = 40 + 32`
        *   **결과 조회 및 확인:**
            ```cmd
            curl http://localhost:8090/api/v1/agent/latest-result
            ```
            *(🎯 예상 신뢰도 점수: **`72` 점**)*

    *   **단계 4: 네 번째 작업의 프로세스 비정상 에러 (FAIL)**
        *   **테스트 실행:**
            ```cmd
            curl -X POST http://localhost:8090/api/v1/agent/run-error-test
            ```
        *   **점수 계산식:** `(성공률 50% * 0.6) + (FAIL 30점 * 0.4) = 30 + 12`
        *   **결과 조회 및 확인:**
            ```cmd
            curl http://localhost:8090/api/v1/agent/latest-result
            ```
            *(🎯 예상 신뢰도 점수: **`42` 점**)*

4.  **요약 검증표 (PR 첨부용)**

    | 단계 | 실행 API | 예상 `testStatus` | 예상 성공률 (이력) | 예상 신뢰도 점수 |
    | :---: | :--- | :---: | :---: | :---: |
    | **1단계** | `run-test` (modify_pass.log) | **PASS** | 100% (최초) | **100점** |
    | **2단계** | `run-test` (modify_fail.log) | **FAIL** | 50% (1/2) | **42점** |
    | **3단계** | `run-test` (no_modify.log) | **SKIPPED** | 66.67% (2/3) | **72점** |
    | **4단계** | `run-error-test` (시스템 에러) | **FAIL** | 50% (2/4) | **42점** |

5.  **DB에서 전체 요약 검증표 확인 방법 (신뢰도 점수 포함)**
    *   테스트 시나리오 수행 후, 데이터베이스에 실제 저장된 이력 전체와 계산된 신뢰도 점수를 한눈에 보려면 아래 명령어를 실행합니다.
    ```cmd
    docker exec postgres psql -U postgres -d agent_harness -c "SELECT r.id, t.status, r.is_success, r.test_status, ROUND((AVG(CASE WHEN r.is_success THEN 100.0 ELSE 0.0 END) OVER (ORDER BY r.id ROWS BETWEEN 9 PRECEDING AND CURRENT ROW) * 0.6) + (CASE r.test_status WHEN 'PASS' THEN 100 WHEN 'SKIPPED' THEN 80 WHEN 'FAIL' THEN 30 ELSE 0 END * 0.4)) AS score, r.test_summary FROM task_results r JOIN agent_tasks t ON r.task_id = t.id ORDER BY r.id;"
    ```

