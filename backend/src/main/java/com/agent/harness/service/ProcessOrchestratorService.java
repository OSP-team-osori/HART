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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.agent.harness.controller.dto.LatestResultResponse;
import java.util.Optional;

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
    public void executeAsync(String prompt, String repoUrl, String githubToken) {
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("An agent process is already running. Only single agent execution is supported in MVP.");
        }

        AgentTask task = AgentTask.builder()
                .prompt(prompt)
                .status(TaskStatus.RUNNING)
                .build();
        AgentTask savedTask = agentTaskRepository.save(task);
        log.info("AgentTask created and saved to DB. ID: {}, Status: RUNNING", savedTask.getId());

        executorService.submit(() -> {
            try {
                runCommandInternal(savedTask, prompt, repoUrl, githubToken);
            } finally {
                isRunning.set(false);
            }
        });
    }

    private void runCommandInternal(AgentTask task, String prompt, String repoUrl, String githubToken) {
        long startTime = System.currentTimeMillis();
        int exitCode = -1;
        String lastLine = null;
        // 요청 토큰 우선, 없으면 환경변수 사용
        String resolvedToken = (githubToken != null && !githubToken.isEmpty())
                ? githubToken
                : System.getenv("GITHUB_TOKEN");
        try {
            File baseDir = getProjectRootDir();
            File taskDir = new File(baseDir, "workspace");
            String branchName = setupWorkspace(taskDir, repoUrl, task.getId(), resolvedToken);
            
            // [수정 포인트 1] 로그 파일의 경로를 절대 경로로 변환합니다.
            if (prompt.contains("ping") || prompt.isEmpty()) {
                File dummyLog = new File(baseDir, "scripts/dummy_logs/modify_pass.log");
                prompt = dummyLog.getAbsolutePath(); 
            } else if (prompt.endsWith(".log")) {
                File logFile = new File(prompt);
                if (!logFile.isAbsolute()) {
                    logFile = new File(baseDir, prompt);
                }
                prompt = logFile.getAbsolutePath();
            }

            ProcessBuilder processBuilder;
            
            String pythonCmd = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";

            // [수정 포인트 2] 실행할 파이썬 스크립트의 경로를 baseDir을 이용해 절대 경로로 지정합니다.
            if (prompt.endsWith(".log")) {
                File scriptFile = new File(baseDir, "scripts/agent_wrapper.py");
                processBuilder = new ProcessBuilder(pythonCmd, scriptFile.getAbsolutePath(), "--mock", prompt);
                log.info("Starting Mock offline simulation via agent_wrapper.py for Task ID {}: {}", task.getId(), prompt);
            } else {
                File scriptFile = new File(baseDir, "backend/run_wrapper.py");
                processBuilder = new ProcessBuilder(pythonCmd, scriptFile.getAbsolutePath(), prompt);
                log.info("Starting Real online agent trigger via run_wrapper.py for Task ID {}: {}", task.getId(), prompt);
            }

            // [핵심 유지] 실행 위치(Current Working Directory)는 workspace로 지정!
            // 파이썬 파일은 절대경로로 가져와서 실행하지만, 파이썬이 생성/수정하는 파일들은 모두 taskDir(workspace) 안에 생깁니다.
            processBuilder.directory(taskDir); 

            processBuilder.redirectErrorStream(true);
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

                // 3. GitHub PR 생성 (repoUrl이 있고 코드 수정이 있을 때)
                if (repoUrl != null && !repoUrl.isEmpty() && branchName != null && !"SKIPPED".equalsIgnoreCase(testStatus)) {
                    pushAndCreatePR(taskDir, branchName, repoUrl, task.getPrompt(), testSummary, resolvedToken);
                }

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

    private String setupWorkspace(File taskDir, String repoUrl, Long taskId, String token) throws Exception {
        if (repoUrl != null && !repoUrl.isEmpty()) {
            if (taskDir.exists()) {
                Files.walk(taskDir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }

            String cloneUrl = (token != null && !token.isEmpty())
                ? repoUrl.replace("https://", "https://" + token + "@")
                : repoUrl;

            ProcessBuilder clonePb = new ProcessBuilder("git", "clone", cloneUrl, taskDir.getAbsolutePath());
            clonePb.directory(taskDir.getParentFile());
            clonePb.redirectErrorStream(true);
            Process cloneProcess = clonePb.start();
            cloneProcess.waitFor();
            log.info("Cloned repo: {}", repoUrl);

            String branchName = "hart/task-" + taskId;
            ProcessBuilder branchPb = new ProcessBuilder("git", "checkout", "-b", branchName);
            branchPb.directory(taskDir);
            Process branchProcess = branchPb.start();
            branchProcess.waitFor();
            log.info("Created branch: {}", branchName);

            return branchName;
        } else {
            if (!taskDir.exists()) {
                taskDir.mkdirs();
                ProcessBuilder gitInitPb = new ProcessBuilder("git", "init");
                gitInitPb.directory(taskDir);
                Process gitProcess = gitInitPb.start();
                gitProcess.waitFor();
                log.info("git init 완료: {}", taskDir.getAbsolutePath());
            }
            return null;
        }
    }

    private void pushAndCreatePR(File taskDir, String branchName, String repoUrl, String prompt, String testSummary, String token) {
        try {
            if (token == null || token.isEmpty()) {
                log.warn("GITHUB_TOKEN이 설정되지 않아 PR 생성을 건너뜁니다.");
                broadcastLog("[INFO] GITHUB_TOKEN이 없어 PR 자동 생성을 건너뜁니다.");
                return;
            }

            ProcessBuilder pushPb = new ProcessBuilder("git", "push", "origin", branchName);
            pushPb.directory(taskDir);
            pushPb.redirectErrorStream(true);
            Process pushProcess = pushPb.start();
            int pushExit = pushProcess.waitFor();

            if (pushExit != 0) {
                log.error("git push 실패 (exit code: {})", pushExit);
                broadcastLog("[ERROR] GitHub 브랜치 push에 실패했습니다.");
                return;
            }
            log.info("브랜치 push 완료: {}", branchName);

            String repoPath = repoUrl
                .replaceFirst("https://github\\.com/", "")
                .replaceAll("\\.git$", "");

            String title = "HART: " + prompt.substring(0, Math.min(60, prompt.length()));
            String body = "🤖 HART 자동 생성 PR\\n\\n**작업 요약:** " + testSummary;
            String jsonBody = String.format(
                "{\"title\":\"%s\",\"body\":\"%s\",\"head\":\"%s\",\"base\":\"main\"}",
                title.replace("\"", "\\\""),
                body.replace("\"", "\\\""),
                branchName
            );

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repoPath + "/pulls"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/vnd.github+json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                log.info("PR 생성 완료: {}", response.body());
                broadcastLog("[SUCCESS] GitHub PR이 자동으로 생성되었습니다: " + branchName);
            } else {
                log.error("PR 생성 실패 (status: {}): {}", response.statusCode(), response.body());
                broadcastLog("[ERROR] PR 생성에 실패했습니다. (status: " + response.statusCode() + ")");
            }

        } catch (Exception e) {
            log.error("PR 생성 중 오류 발생", e);
            broadcastLog("[ERROR] PR 생성 중 오류: " + e.getMessage());
        }
    }

    public Optional<LatestResultResponse> getLatestResult() {
        return taskResultRepository.findFirstByOrderByIdDesc()
                .map(result -> {
                    AgentTask task = result.getTask();
                    int confidenceScore = calculateConfidenceScore(result);

                    return LatestResultResponse.builder()
                            .id(result.getId())
                            .taskId(task.getId())
                            .prompt(task.getPrompt())
                            .status(task.getStatus())
                            .createdAt(task.getCreatedAt())
                            .finishedAt(task.getFinishedAt())
                            .isSuccess(result.isSuccess())
                            .exitCode(result.getExitCode())
                            .executionTimeMs(result.getExecutionTimeMs())
                            .tokensUsed(result.getTokensUsed())
                            .testStatus(result.getTestStatus())
                            .cost(result.getCost())
                            .testSummary(result.getTestSummary())
                            .confidenceScore(confidenceScore)
                            .build();
                });
    }

    private int calculateConfidenceScore(TaskResult currentResult) {
        List<TaskResult> recentResults = taskResultRepository.findTop10ByOrderByIdDesc();

        double successRate = 100.0;
        if (!recentResults.isEmpty()) {
            long successCount = recentResults.stream()
                    .filter(TaskResult::isSuccess)
                    .count();
            successRate = ((double) successCount / recentResults.size()) * 100.0;
        }

        int currentStatusScore = 0;
        String status = currentResult.getTestStatus();
        if (status != null) {
            switch (status.toUpperCase()) {
                case "PASS":    currentStatusScore = 100; break;
                case "SKIPPED": currentStatusScore = 80;  break;
                case "FAIL":    currentStatusScore = 30;  break;
                default:        currentStatusScore = 0;
            }
        }

        double rawScore = (successRate * 0.6) + (currentStatusScore * 0.4);
        return (int) Math.round(rawScore);
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
