package com.agent.harness.repository;

import com.agent.harness.entity.AgentTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTaskRepository extends JpaRepository<AgentTask, Long> {
}
