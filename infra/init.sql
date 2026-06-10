-- ============================================================================
-- [HART TADD] DB 스키마 초기화 (JPA 엔티티 기준 통일)
-- ============================================================================
-- 이 파일은 docker-compose의 PostgreSQL 컨테이너가 최초 초기화될 때
-- /docker-entrypoint-initdb.d/ 경로를 통해 자동 실행됩니다.
-- 기존 agent_runs 단일 테이블 → agent_tasks + task_results 정규화 구조로 통일.
-- JPA 엔티티: AgentTask.java, TaskResult.java 와 1:1 매칭.
-- ============================================================================

-- 기존 agent_runs 테이블 제거 (스키마 불일치 해소)
DROP TABLE IF EXISTS agent_runs CASCADE;

-- 에이전트 작업 테이블 (JPA 엔티티 AgentTask와 일치)
CREATE TABLE IF NOT EXISTS agent_tasks (
    id BIGSERIAL PRIMARY KEY,
    prompt TEXT NOT NULL,
    status VARCHAR(20),           -- PENDING, RUNNING, COMPLETED, FAILED
    created_at TIMESTAMP,
    finished_at TIMESTAMP
);

-- 작업 결과 테이블 (JPA 엔티티 TaskResult와 일치)
CREATE TABLE IF NOT EXISTS task_results (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL UNIQUE,
    is_success BOOLEAN,
    exit_code INTEGER,
    execution_time_ms BIGINT,
    tokens_used INTEGER,
    test_status VARCHAR(20),      -- PASS, FAIL, SKIPPED
    cost DOUBLE PRECISION,
    test_summary TEXT,
    CONSTRAINT fk_task FOREIGN KEY (task_id) REFERENCES agent_tasks(id)
);

-- 초기 더미 데이터 3종 세트 (대시보드 UI 테스트용)
INSERT INTO agent_tasks (prompt, status, created_at, finished_at) VALUES
('Check password encryption logic', 'COMPLETED', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '1 hour 59 minutes'),
('Generate unit tests for auth module', 'FAILED', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '59 minutes'),
('Refactor Dockerfile for size reduction', 'FAILED', NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '29 minutes');

INSERT INTO task_results (task_id, is_success, exit_code, execution_time_ms, tokens_used, test_status, cost, test_summary) VALUES
(1, true,  0, 42500,  1200, 'PASS', 0.024, 'All 5 encryption tests passed successfully.'),
(2, false, 1, 12000,  450,  'FAIL', 0.009, 'AssertionError in test_auth_module: expected 200 but got 401'),
(3, false, -1, 0,     0,    'FAIL', 0.0,   'Process terminated unexpectedly. Dockerfile syntax error at line 12.');