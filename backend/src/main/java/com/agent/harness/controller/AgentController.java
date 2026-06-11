package com.agent.harness.controller;

import com.agent.harness.service.ProcessOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final ProcessOrchestratorService processOrchestratorService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() {
        return processOrchestratorService.subscribe();
    }
    @PostMapping("/run-test")
    public ResponseEntity<String> runTestProcess(@RequestBody(required = false) java.util.Map<String, String> payload) {
        try {
            String prompt = (payload != null) ? payload.get("prompt") : null;
            String repoUrl = (payload != null) ? payload.get("repoUrl") : null;
            String targetPrompt = (prompt == null || prompt.trim().isEmpty())
                    ? "scripts/dummy_logs/modify_pass.log"
                    : prompt.trim();

            processOrchestratorService.executeAsync(targetPrompt, repoUrl);
            return ResponseEntity.ok("Test process started asynchronously with target: " + targetPrompt);
        } catch (IllegalStateException e) {
            // 중복 실행 시 409 Conflict 반환
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @PostMapping("/run-error-test")
    public ResponseEntity<String> runErrorTestProcess() {
        try {
            // 존재하지 않는 명령어를 고의로 실행하여 예외 처리 테스트
            processOrchestratorService.executeAsync("cmd.exe", "/c", "invalid_command_abc_123");
            return ResponseEntity.ok("Error test process started.");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<String> stopProcess() {
        processOrchestratorService.stopProcess();
        return ResponseEntity.ok("Process stop signal sent.");
    }
}
