package com.agent.harness.controller;

import com.agent.harness.service.ProcessOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final ProcessOrchestratorService processOrchestratorService;

    @PostMapping("/run-test")
    public ResponseEntity<String> runTestProcess() {
        try {
            // 안티패턴(new Thread()) 제거: 서비스 단의 ExecutorService 풀을 사용하여 안전하게 비동기 호출
            processOrchestratorService.executeAsync("cmd.exe", "/c", "ping", "127.0.0.1", "-n", "10"); // 강제 종료 테스트를 위해 10번으로 늘림
            return ResponseEntity.ok("Test process started asynchronously. Check backend console for logs.");
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
