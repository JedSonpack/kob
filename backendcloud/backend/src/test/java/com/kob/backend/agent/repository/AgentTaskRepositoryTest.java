package com.kob.backend.agent.repository;

import com.kob.backend.agent.mapper.AgentTaskMapper;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.model.AgentTaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTaskRepositoryTest {

    @Test
    void transitionReturnsTrueWhenCasAffectsOneRow() {
        AgentTaskMapper mapper = mock(AgentTaskMapper.class);
        AgentTask task = new AgentTask();
        task.setId(12L);
        task.setVersion(3);
        task.setStatus("COMPILING");
        when(mapper.compareAndSetStatus(12L, 3, "COMPILING", "EVALUATING",
                1, null, null, null, false)).thenReturn(1);

        AgentTaskRepository repository = new AgentTaskRepository(mapper);

        assertTrue(repository.transition(task, AgentTaskStatus.EVALUATING, 1,
                null, null, null, false));
    }

    @Test
    void transitionReturnsFalseAndDoesNotMutateWhenCasMisses() {
        AgentTaskMapper mapper = mock(AgentTaskMapper.class);
        AgentTask task = new AgentTask();
        task.setId(12L);
        task.setVersion(3);
        task.setStatus("COMPILING");
        when(mapper.compareAndSetStatus(anyLong(), anyInt(), anyString(), anyString(),
                any(), any(), any(), any(), anyBoolean())).thenReturn(0);

        AgentTaskRepository repository = new AgentTaskRepository(mapper);

        assertFalse(repository.transition(task, AgentTaskStatus.EVALUATING, 1,
                null, null, null, false));
        assertEquals("COMPILING", task.getStatus());
        assertEquals(3, task.getVersion());
    }
}
