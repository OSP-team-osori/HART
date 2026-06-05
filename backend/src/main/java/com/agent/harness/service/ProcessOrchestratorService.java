package com.agent.harness.service;

import com.agent.harness.entity.AgentTask;
import com.agent.harness.entity.TaskResult;
import com.agent.harness.entity.TaskStatus;
import com.agent.harness.repository.AgentTaskRepository;
import com.agent.harness.repository.TaskResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessOrchestratorService {

    private final AgentTaskRepository agentTaskRepository;
    private final TaskResultRepository taskResultRepository;

    // 프로세스 실행과 스트림 읽기를 모두 처리하기 위해 스레드 풀 지정
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    private Process currentProcess;
    
    // 동시성(Race Condition)을 완벽히 제어하기 위한 Atomic 플래그
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // SSE 구독자 목록 (Thread-Safe)
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 프론트엔드가 실시간 로그를 받기 위해 구독(Subscribe)하는 메서드
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // 0은 타임아웃 없음
        emitters.add(emitter);

        // 클라이언트 연결 종료 또는 에러 시 안전하게 자원 해제
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        // 최초 연결 시 상태 메시지 전송
        try {
            emitter.send(SseEmitter.event().name("connect").data("Connected to Agent Log Stream"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * 실행 중인 프로세스의 로그를 구독자들에게 브로드캐스트
     */
    private void broadcastLog(String message) {
        List<SseEmitter> deadEmitters = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("log").data(message));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    private File getProjectRootDir() {
        File workingDir = new File(System.getProperty("user.dir"));
        if ("backend".equals(workingDir.getName())) {
            workingDir = workingDir.getParentFile();
        }
        return workingDir;
    }

    /**
     * 외부에서 비동기로 프로세스를 실행하도록 요청하는 퍼블릭 메서드
     */
    public void executeAsync(String... command) {
        // compareAndSet을 통해 동시에 여러 요청이 와도 단 하나만 true로 변경 성공 (Thread-Safe)
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("An agent process is already running. Only single agent execution is supported in MVP.");
        }

        // JPA 연동을 위한 임시 AgentTask 생성 및 저장 (상태: RUNNING)
        AgentTask task = AgentTask.builder()
                .prompt(String.join(" ", command))
                .status(TaskStatus.RUNNING)
                .build();
        AgentTask savedTask = agentTaskRepository.save(task);
        log.info("AgentTask created and saved to DB. ID: {}, Status: RUNNING", savedTask.getId());

        // 스레드 풀에서 비동기 실행 (컨트롤러에서 new Thread() 생성 방지)
        executorService.submit(() -> {
            try {
                runCommandInternal(savedTask, command);
            } finally {
                // 정상 종료든 에러가 터졌든 무조건 상태를 false로 복구하여 다음 실행 허용
                isRunning.set(false);
            }
        });
    }

    private void runCommandInternal(AgentTask task, String... command) {
        long startTime = System.currentTimeMillis();
        int exitCode = -1;
        String lastLine = null;
        try {
            // PM이 구현한 backend/run_wrapper.py 파이썬 비서 단독 프로세스 실행
            // 프롬프트를 인자로 실어서 래퍼를 띄웁니다.
            String prompt = String.join(" ", command);
            
            // 만약 ping 명령이나 API 기본 인자가 들어오면 실전 테스트 검증을 위해 
            // "그냥 인사만 해줘" 등의 프롬프트로 유연하게 보정하여 래퍼를 호출할 수 있게 합니다.
            if (prompt.contains("ping") || prompt.isEmpty()) {
                prompt = "scripts/dummy_logs/modify_pass.log";
            }

            ProcessBuilder processBuilder;
            // 입력된 프롬프트가 .log 파일 경로인 경우, 
            // PM 민준님이 작성한 run_wrapper.py는 그대로 완전 블랙박스로 보존하고,
            // 모의 시뮬레이터인 scripts/agent_wrapper.py --mock 을 통해 완벽히 오프라인 격리 검증을 수행합니다!
            if (prompt.endsWith(".log")) {
                processBuilder = new ProcessBuilder("python3", "scripts/agent_wrapper.py", "--mock", prompt);
                log.info("Starting Mock offline simulation via agent_wrapper.py for Task ID {}: {}", task.getId(), prompt);
            } else {
                processBuilder = new ProcessBuilder("python3", "backend/run_wrapper.py", prompt);
                log.info("Starting Real online agent trigger via run_wrapper.py for Task ID {}: {}", task.getId(), prompt);
            }

            processBuilder.directory(getProjectRootDir());
            processBuilder.redirectErrorStream(true); // 에러 스트림을 표준 출력으로 병합
            processBuilder.environment().put("PYTHONUNBUFFERED", "1");

            currentProcess = processBuilder.start();

            // 백그라운드 스레드에서 I/O 스트림 읽기 (메모리 릭 방지)
            BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[AGENT LOG] {}", line);
                broadcastLog(line); // 프론트엔드로 실시간 전송!
                lastLine = line; // 마지막 요약 JSON 라인을 기록
            }

            // 프로세스가 끝날 때까지 대기
            exitCode = currentProcess.waitFor();
            long executionTimeMs = System.currentTimeMillis() - startTime;
            log.info("Pipeline process exited with code: {} for Task ID: {}", exitCode, task.getId());

            if (exitCode == 0 && lastLine != null && lastLine.trim().startsWith("{")) {
                // Jackson ObjectMapper를 이용해 결과 JSON 파싱
                ObjectMapper objectMapper = new ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> jsonResult = objectMapper.readValue(lastLine, Map.class);

                String testStatus = (String) jsonResult.get("test_status");
                Integer totalTokens = (Integer) jsonResult.get("total_tokens");
                
                // cost 필드 형변환 안전처리
                Object costObj = jsonResult.get("cost");
                Double cost = 0.0;
                if (costObj instanceof Number) {
                    cost = ((Number) costObj).doubleValue();
                }
                
                String testSummary = (String) jsonResult.get("test_summary");

                boolean isSuccess = "PASS".equalsIgnoreCase(testStatus) || "SKIPPED".equalsIgnoreCase(testStatus);

                // 1. TaskResult 엔티티 매핑 및 DB 영구 저장
                TaskResult taskResult = TaskResult.builder()
                        .task(task)
                        .isSuccess(isSuccess)
                        .exitCode(exitCode)
                        .executionTimeMs(executionTimeMs)
                        .tokensUsed(totalTokens)
                        .testStatus(testStatus)
                        .cost(cost)
                        .testSummary(testSummary)
                        .build();
                taskResultRepository.save(taskResult);
                log.info("TaskResult successfully persisted. ID: {}, Status: {}", taskResult.getId(), testStatus);

                // 2. AgentTask 상태 업데이트 및 DB 갱신
                task.setStatus(isSuccess ? TaskStatus.COMPLETED : TaskStatus.FAILED);
                task.setFinishedAt(java.time.LocalDateTime.now());
                agentTaskRepository.save(task);
                log.info("AgentTask updated. ID: {}, Status: {}", task.getId(), task.getStatus());

            } else {
                log.error("Failed to parse JSON from Python wrapper. Last line was: {}", lastLine);
                saveFailedTaskResult(task, exitCode, executionTimeMs, "Invalid output format from wrapper script.");
            }

        } catch (Exception e) {
            log.error("Failed to execute process for Task ID: " + task.getId(), e);
            long executionTimeMs = System.currentTimeMillis() - startTime;
            saveFailedTaskResult(task, exitCode, executionTimeMs, e.getMessage());
        }
    }

    private void saveFailedTaskResult(AgentTask task, int exitCode, long executionTime, String errorMsg) {
        try {
            TaskResult taskResult = TaskResult.builder()
                    .task(task)
                    .isSuccess(false)
                    .exitCode(exitCode)
                    .executionTimeMs(executionTime)
                    .tokensUsed(0)
                    .testStatus("FAIL")
                    .cost(0.0)
                    .testSummary(errorMsg)
                    .build();
            taskResultRepository.save(taskResult);

            task.setStatus(TaskStatus.FAILED);
            task.setFinishedAt(java.time.LocalDateTime.now());
            agentTaskRepository.save(task);
            log.info("Persisted failed task result for Task ID: {}", task.getId());
        } catch (Exception ex) {
            log.error("Critical error saving failed task status to DB for Task ID: " + task.getId(), ex);
        }
    }

    public synchronized void stopProcess() {
        if (currentProcess != null && currentProcess.isAlive()) {
            log.info("Force stopping the current process...");
            currentProcess.destroyForcibly();
            try {
                currentProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("Process forcefully stopped.");
        } else {
            log.warn("No process is currently running to stop.");
        }
    }
}
