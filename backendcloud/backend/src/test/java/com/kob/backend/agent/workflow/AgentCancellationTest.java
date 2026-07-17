package com.kob.backend.agent.workflow;

import com.kob.backend.agent.llm.LlmStepExecutor;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.model.BotVersion;
import com.kob.backend.agent.model.DatasetType;
import com.kob.backend.agent.tool.AgentToolRouter;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentCancellationTest {

    private static AgentTask task(String status, int iteration, Integer userId) {
        AgentTask t = new AgentTask();
        t.setId(10L);
        t.setUserId(userId);
        t.setStatus(status);
        t.setCurrentIteration(iteration);
        t.setMaxIterations(3);
        t.setVersion(0);
        t.setCreatedAt(new Date());
        t.setUpdatedAt(new Date());
        return t;
    }

    private static BotVersion version(Long id, Long taskId, int iteration) {
        BotVersion v = new BotVersion();
        v.setId(id);
        v.setTaskId(taskId);
        v.setIteration(iteration);
        v.setAttempt(1);
        v.setCompileStatus("SUCCESS");
        v.setSourceCode("package com.kob.test;");
        v.setStrategySummary("s");
        return v;
    }

    private AgentWorkflowServiceImpl workflow(InMemoryAgentRepositories repos,
                                               AgentToolRouter toolRouter,
                                               AgentWorkflowExecutor executor) {
        AgentWorkflowServiceImpl wf = new AgentWorkflowServiceImpl(repos.taskRepository,
                repos.versionRepository, repos.evaluationRunRepository, mock(LlmStepExecutor.class),
                toolRouter, new BestVersionSelector(), 100L);
        wf.setAgentWorkflowExecutor(executor);
        return wf;
    }

    @Test
    void cancelTaskMovesToCancelledAndCancelsRemoteAndLocal() {
        InMemoryAgentRepositories repos = new InMemoryAgentRepositories();
        AgentTask task = task("EVALUATING", 1, 7);
        repos.taskRepository.seed(task);
        repos.versionRepository.save(version(1L, task.getId(), 1));
        AgentToolRouter toolRouter = mock(AgentToolRouter.class);
        AgentWorkflowExecutor executor = mock(AgentWorkflowExecutor.class);

        workflow(repos, toolRouter, executor).cancelTask(task.getId(), 7);

        assertEquals("CANCELLED", repos.taskRepository.findById(task.getId()).getStatus());
        assertEquals(null, repos.taskRepository.findById(task.getId()).getActiveSlot());
        verify(toolRouter).cancel(task.getId(), 1L, DatasetType.PUBLIC);
        verify(executor).cancel(task.getId());
    }

    @Test
    void cancelTerminalTaskIsNoop() {
        InMemoryAgentRepositories repos = new InMemoryAgentRepositories();
        AgentTask task = task("COMPLETED", 3, 7);
        repos.taskRepository.seed(task);
        AgentToolRouter toolRouter = mock(AgentToolRouter.class);
        AgentWorkflowExecutor executor = mock(AgentWorkflowExecutor.class);

        workflow(repos, toolRouter, executor).cancelTask(task.getId(), 7);

        assertEquals("COMPLETED", repos.taskRepository.findById(task.getId()).getStatus());
        verify(toolRouter, never()).cancel(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(executor, never()).cancel(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void cancelWrongUserIsNoop() {
        InMemoryAgentRepositories repos = new InMemoryAgentRepositories();
        AgentTask task = task("EVALUATING", 1, 7);
        repos.taskRepository.seed(task);
        AgentToolRouter toolRouter = mock(AgentToolRouter.class);
        AgentWorkflowExecutor executor = mock(AgentWorkflowExecutor.class);

        workflow(repos, toolRouter, executor).cancelTask(task.getId(), 999);

        assertEquals("EVALUATING", repos.taskRepository.findById(task.getId()).getStatus());
        verify(executor, never()).cancel(org.mockito.ArgumentMatchers.anyLong());
    }
}
