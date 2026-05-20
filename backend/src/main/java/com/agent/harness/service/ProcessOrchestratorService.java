package com.agent.harness.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
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
public class ProcessOrchestratorService {

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
    /**
     * 외부에서 비동기로 프로세스를 실행하도록 요청하는 퍼블릭 메서드
     */
    public void executeAsync(String... command) {
        // compareAndSet을 통해 동시에 여러 요청이 와도 단 하나만 true로 변경 성공 (Thread-Safe)
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("An agent process is already running. Only single agent execution is supported in MVP.");
        }

        // 스레드 풀에서 비동기 실행 (컨트롤러에서 new Thread() 생성 방지)
        executorService.submit(() -> {
            try {
                runCommandInternal(command);
            } finally {
                // 정상 종료든 에러가 터졌든 무조건 상태를 false로 복구하여 다음 실행 허용
                isRunning.set(false);
            }
        });
    }

    private void runCommandInternal(String... command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(System.getProperty("user.dir")));
            processBuilder.redirectErrorStream(true); // 에러 스트림을 표준 출력으로 병합
            processBuilder.environment().put("PYTHONUNBUFFERED", "1");

            log.info("Starting process: {}", String.join(" ", command));
            currentProcess = processBuilder.start();

            // 백그라운드 스레드에서 I/O 스트림 읽기 (메모리 릭 방지)
            executorService.submit(() -> readStream(currentProcess.getInputStream()));

            // 프로세스가 끝날 때까지 대기
            int exitCode = currentProcess.waitFor();
            log.info("Process exited with code: {}", exitCode);

        } catch (Exception e) {
            log.error("Failed to execute process: {}", e.getMessage());
            // 실제 환경에서는 이 정보를 DB(TaskResult)에 실패로 기록해야 합니다.
        }
    }

    private void readStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[AGENT LOG] {}", line);
                broadcastLog(line); // 프론트엔드로 실시간 전송!
            }
        } catch (Exception e) {
            log.error("Error reading process stream", e);
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
