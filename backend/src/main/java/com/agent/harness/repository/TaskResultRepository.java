package com.agent.harness.repository;

import com.agent.harness.entity.TaskResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskResultRepository extends JpaRepository<TaskResult, Long> {
    Optional<TaskResult> findFirstByOrderByIdDesc();
    List<TaskResult> findTop10ByOrderByIdDesc();

    @org.springframework.data.jpa.repository.Query("SELECT SUM(t.tokensUsed) FROM TaskResult t")
    Integer sumTokensUsed();

    @org.springframework.data.jpa.repository.Query("SELECT SUM(t.cost) FROM TaskResult t")
    Double sumCost();
}
