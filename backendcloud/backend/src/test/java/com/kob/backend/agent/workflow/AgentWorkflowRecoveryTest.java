package com.kob.backend.agent.workflow;

import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.repository.AgentTaskRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentWorkflowRecoveryTest {

    @Test
    void onApplicationReadySubmitsIncompleteTasks() {
        AgentTaskRepository taskRepo = mock(AgentTaskRepository.class);
        AgentWorkflowExecutor executor = mock(AgentWorkflowExecutor.class);
        AgentTask t1 = new AgentTask();
        t1.setId(1L);
        AgentTask t2 = new AgentTask();
        t2.setId(2L);
        when(taskRepo.findIncompleteTasks()).thenReturn(Arrays.asList(t1, t2));

        AgentWorkflowRecovery recovery = new AgentWorkflowRecovery(taskRepo, executor);
        recovery.onApplicationReady();

        verify(executor).submit(1L);
        verify(executor).submit(2L);
    }
}
