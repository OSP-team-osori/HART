package com.agent.harness.controller.dto;

import com.agent.harness.entity.TaskStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LatestResultResponse {
    private Long id;
    private Long taskId;
    private String prompt;
    private TaskStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    
    private boolean isSuccess;
    private Integer exitCode;
    private Long executionTimeMs;
    private Integer tokensUsed;
    private String testStatus;
    private Double cost;
    private String testSummary;
    
    private Integer confidenceScore;
}
