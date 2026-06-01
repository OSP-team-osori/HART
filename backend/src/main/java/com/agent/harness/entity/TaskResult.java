package com.agent.harness.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "task_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "task_id", nullable = false)
    private AgentTask task;

    private boolean isSuccess; // pytest 통과 여부

    private Integer exitCode;

    private Long executionTimeMs; // 소요 시간

    private Integer tokensUsed; // 토큰 사용량

    private String testStatus; // PASS, FAIL, SKIPPED

    private Double cost; // 사용 비용

    @Column(columnDefinition = "TEXT")
    private String testSummary; // 검증 결과 요약
}
