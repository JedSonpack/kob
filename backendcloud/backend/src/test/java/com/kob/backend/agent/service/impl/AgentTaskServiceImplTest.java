package com.kob.backend.agent.service.impl;

import com.kob.backend.agent.dto.CreateAgentTaskRequest;
import com.kob.backend.agent.repository.AgentStepRepository;
import com.kob.backend.agent.repository.AgentTaskRepository;
import com.kob.backend.agent.repository.BotVersionRepository;
import com.kob.backend.agent.repository.EvaluationRunRepository;
import com.kob.backend.agent.tool.AgentToolRouter;
import com.kob.backend.agent.workflow.AgentWorkflowExecutor;
import com.kob.backend.agent.workflow.AgentWorkflowService;
import com.kob.backend.pojo.User;
import com.kob.backend.service.impl.utils.UserDetailsImpl;
import com.kob.backend.service.user.bot.AddService;
import com.kob.backend.agent.model.AgentTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskServiceImplTest {

    private AgentTaskRepository taskRepo;
    private AgentWorkflowExecutor executor;
    private AgentTaskServiceImpl service;

    @BeforeEach
    void setup() {
        taskRepo = mock(AgentTaskRepository.class);
        service = new AgentTaskServiceImpl(taskRepo,
                mock(BotVersionRepository.class), mock(EvaluationRunRepository.class),
                mock(AgentStepRepository.class), mock(AgentToolRouter.class),
                executor = mock(AgentWorkflowExecutor.class),
                mock(AgentWorkflowService.class), mock(AddService.class));
        setCurrentUser(7);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void setCurrentUser(int userId) {
        User user = new User();
        user.setId(userId);
        UserDetailsImpl ud = mock(UserDetailsImpl.class);
        when(ud.getUser()).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ud, null));
    }

    private CreateAgentTaskRequest request(String goal, Integer max) {
        CreateAgentTaskRequest r = new CreateAgentTaskRequest();
        r.setGoal(goal);
        r.setMaxIterations(max);
        return r;
    }

    @Test
    void createTaskInsertsAndSubmits() {
        when(taskRepo.findByUser(7)).thenReturn(Collections.<AgentTask>emptyList());
        doAnswer(inv -> { ((AgentTask) inv.getArgument(0)).setId(1L); return null; })
                .when(taskRepo).insert(any());

        Long id = service.createTask(request("扩大可活动区域", 3));

        assertEquals(Long.valueOf(1L), id);
        verify(executor).submit(1L);
    }

    @Test
    void rejectsEmptyGoal() {
        assertThrows(AgentTaskException.class, () -> service.createTask(request("", 3)));
    }

    @Test
    void rejectsMaxIterationsOutOfRange() {
        assertThrows(AgentTaskException.class, () -> service.createTask(request("goal", 4)));
        assertThrows(AgentTaskException.class, () -> service.createTask(request("goal", 0)));
    }

    @Test
    void rejectsWhenActiveTaskExists() {
        AgentTask active = new AgentTask();
        active.setActiveSlot(1);
        when(taskRepo.findByUser(7)).thenReturn(Arrays.asList(active));
        assertThrows(AgentTaskConflictException.class, () -> service.createTask(request("goal", 3)));
    }
}
