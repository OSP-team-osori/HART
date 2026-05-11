-- 에이전트 실행 기록 테이블 (대시보드 메인)
CREATE TABLE IF NOT EXISTS agent_runs (
    id SERIAL PRIMARY KEY,
    run_id VARCHAR(50) NOT NULL UNIQUE,
    agent_name VARCHAR(50),
    task_prompt TEXT,
    status VARCHAR(20), -- SUCCESS, FAIL, ERROR
    execution_time_seconds FLOAT,
    total_tokens_used INTEGER,
    reliability_score FLOAT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 초기 더미 데이터 3종 세트 (대시보드 UI 테스트용)
INSERT INTO agent_runs (run_id, agent_name, task_prompt, status, execution_time_seconds, total_tokens_used, reliability_score)
VALUES 
('task_001', 'Aider', 'Check password encryption logic', 'SUCCESS', 42.5, 1200, 95.0),
('task_002', 'Codex', 'Generate unit tests for auth module', 'FAIL', 12.0, 450, 40.5),
('task_003', 'Gemini', 'Refactor Dockerfile for size reduction', 'ERROR', 0.0, 0, 0.0);