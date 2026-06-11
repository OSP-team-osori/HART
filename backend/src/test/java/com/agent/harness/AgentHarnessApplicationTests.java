package com.agent.harness;

import com.agent.harness.controller.dto.LatestResultResponse;
import com.agent.harness.entity.AgentTask;
import com.agent.harness.entity.TaskResult;
import com.agent.harness.entity.TaskStatus;
import com.agent.harness.repository.AgentTaskRepository;
import com.agent.harness.repository.TaskResultRepository;
import com.agent.harness.service.ProcessOrchestratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AgentHarnessApplicationTests {

	@Autowired
	private ProcessOrchestratorService processOrchestratorService;

	@Autowired
	private AgentTaskRepository agentTaskRepository;

	@Autowired
	private TaskResultRepository taskResultRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void testGetLatestResultAndConfidenceScore() {
		// Clean up existing if any (since it is @Transactional, it will roll back anyway)
		taskResultRepository.deleteAll();
		agentTaskRepository.deleteAll();

		// Save a successful task and result
		AgentTask task1 = AgentTask.builder()
				.prompt("Task 1")
				.status(TaskStatus.COMPLETED)
				.build();
		agentTaskRepository.save(task1);

		TaskResult result1 = TaskResult.builder()
				.task(task1)
				.isSuccess(true)
				.testStatus("PASS")
				.exitCode(0)
				.executionTimeMs(1000L)
				.tokensUsed(100)
				.cost(0.01)
				.testSummary("PASS summary")
				.build();
		taskResultRepository.save(result1);

		// Save a failed task and result
		AgentTask task2 = AgentTask.builder()
				.prompt("Task 2")
				.status(TaskStatus.FAILED)
				.build();
		agentTaskRepository.save(task2);

		TaskResult result2 = TaskResult.builder()
				.task(task2)
				.isSuccess(false)
				.testStatus("FAIL")
				.exitCode(1)
				.executionTimeMs(2000L)
				.tokensUsed(200)
				.cost(0.02)
				.testSummary("FAIL summary")
				.build();
		taskResultRepository.save(result2);

		// Request latest result
		Optional<LatestResultResponse> responseOpt = processOrchestratorService.getLatestResult();
		assertThat(responseOpt).isPresent();

		LatestResultResponse response = responseOpt.get();
		assertThat(response.getPrompt()).isEqualTo("Task 2");
		assertThat(response.isSuccess()).isFalse();
		assertThat(response.getTestStatus()).isEqualTo("FAIL");

		// Confidence Score Calculation:
		// recentResults size = 2 (task1=PASS, task2=FAIL). Success count = 1. successRate = 50.0%
		// currentStatusScore = 30 (FAIL)
		// rawScore = (50.0 * 0.6) + (30 * 0.4) = 30.0 + 12.0 = 42.0
		// rounded score = 42
		assertThat(response.getConfidenceScore()).isEqualTo(42);
	}

	@Test
	void testConfidenceScoreWithSkippedStatus() {
		taskResultRepository.deleteAll();
		agentTaskRepository.deleteAll();

		// Save a single task with status SKIPPED
		AgentTask task = AgentTask.builder()
				.prompt("Task SKIPPED")
				.status(TaskStatus.COMPLETED)
				.build();
		agentTaskRepository.save(task);

		TaskResult result = TaskResult.builder()
				.task(task)
				.isSuccess(true) // SKIPPED is treated as successful in ProcessOrchestratorService:203
				.testStatus("SKIPPED")
				.exitCode(0)
				.executionTimeMs(500L)
				.tokensUsed(50)
				.cost(0.005)
				.testSummary("SKIPPED summary")
				.build();
		taskResultRepository.save(result);

		Optional<LatestResultResponse> responseOpt = processOrchestratorService.getLatestResult();
		assertThat(responseOpt).isPresent();

		LatestResultResponse response = responseOpt.get();
		// Calculation:
		// successRate = 100.0% (1 run, 1 success)
		// currentStatusScore = 80 (SKIPPED)
		// rawScore = (100.0 * 0.6) + (80 * 0.4) = 60.0 + 32.0 = 92.0
		assertThat(response.getConfidenceScore()).isEqualTo(92);
	}

	@Test
	void testConfidenceScoreRecentTenOnly() {
		taskResultRepository.deleteAll();
		agentTaskRepository.deleteAll();

		// Save 5 failed tasks (oldest)
		for (int i = 1; i <= 5; i++) {
			AgentTask task = AgentTask.builder().prompt("Old Fail " + i).status(TaskStatus.FAILED).build();
			agentTaskRepository.save(task);
			TaskResult result = TaskResult.builder().task(task).isSuccess(false).testStatus("FAIL").build();
			taskResultRepository.save(result);
		}

		// Save 9 successful tasks
		for (int i = 1; i <= 9; i++) {
			AgentTask task = AgentTask.builder().prompt("Recent Pass " + i).status(TaskStatus.COMPLETED).build();
			agentTaskRepository.save(task);
			TaskResult result = TaskResult.builder().task(task).isSuccess(true).testStatus("PASS").build();
			taskResultRepository.save(result);
		}

		// Save the latest task (which fails)
		AgentTask latestTask = AgentTask.builder().prompt("Latest Fail").status(TaskStatus.FAILED).build();
		agentTaskRepository.save(latestTask);
		TaskResult latestResult = TaskResult.builder().task(latestTask).isSuccess(false).testStatus("FAIL").build();
		taskResultRepository.save(latestResult);

		// Now we have 15 runs in total.
		// findTop10ByOrderByIdDesc should only retrieve the latest 10 runs:
		// - 9 recent passes
		// - 1 latest fail
		// Out of these 10 runs, 9 are successful -> successRate = 90.0%
		// The current task is "FAIL" -> currentStatusScore = 30
		// Score = (90.0 * 0.6) + (30 * 0.4) = 54.0 + 12.0 = 66.0
		
		Optional<LatestResultResponse> responseOpt = processOrchestratorService.getLatestResult();
		assertThat(responseOpt).isPresent();
		assertThat(responseOpt.get().getConfidenceScore()).isEqualTo(66);
	}
}
