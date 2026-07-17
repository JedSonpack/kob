package com.kob.backend.agent.service.impl;

import com.kob.backend.agent.dto.AgentRunSummaryDto;
import com.kob.backend.agent.dto.AgentTaskDetailDto;
import com.kob.backend.agent.dto.AgentVersionDetailDto;
import com.kob.backend.agent.dto.CreateAgentTaskRequest;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.model.BotVersion;
import com.kob.backend.agent.model.DatasetType;
import com.kob.backend.agent.model.EvaluationRun;
import com.kob.backend.agent.repository.AgentStepRepository;
import com.kob.backend.agent.repository.AgentTaskRepository;
import com.kob.backend.agent.repository.BotVersionRepository;
import com.kob.backend.agent.repository.EvaluationRunRepository;
import com.kob.backend.agent.tool.AgentToolRouter;
import com.kob.backend.agent.tool.EvaluationAggregate;
import com.kob.backend.agent.workflow.AgentWorkflowExecutor;
import com.kob.backend.agent.workflow.AgentWorkflowService;
import com.kob.backend.pojo.User;
import com.kob.backend.service.impl.utils.UserDetailsImpl;
import com.kob.backend.service.user.bot.AddService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskServiceImplTest {

    private AgentTaskRepository taskRepo;
    private BotVersionRepository versionRepo;
    private EvaluationRunRepository runRepo;
    private AgentStepRepository stepRepo;
    private AgentToolRouter toolRouter;
    private AgentWorkflowExecutor executor;
    private AgentTaskServiceImpl service;

    @BeforeEach
    void setup() {
        taskRepo = mock(AgentTaskRepository.class);
        versionRepo = mock(BotVersionRepository.class);
        runRepo = mock(EvaluationRunRepository.class);
        stepRepo = mock(AgentStepRepository.class);
        toolRouter = mock(AgentToolRouter.class);
        service = new AgentTaskServiceImpl(taskRepo, versionRepo, runRepo, stepRepo,
                toolRouter, executor = mock(AgentWorkflowExecutor.class),
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

    private AgentTask task(Long id, int userId, String status) {
        AgentTask t = new AgentTask();
        t.setId(id);
        t.setUserId(userId);
        t.setStatus(status);
        return t;
    }

    private EvaluationAggregate publicAggregate() {
        return new EvaluationAggregate(DatasetType.PUBLIC, 48, 0.6, 0.5, 40.0, 80L, 0,
                Collections.emptyMap());
    }

    private EvaluationRun run(Long id, Long versionId, String dataset, String opponent,
                              String side, String result, Integer rounds, String failureReason) {
        return new EvaluationRun(id, versionId, dataset, opponent, 123L, side, result, rounds,
                80L, 0, failureReason, null, new Date());
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

    @Test
    void getVersionDetailExposesSourceAndParent() {
        BotVersion version = new BotVersion();
        version.setId(10L);
        version.setTaskId(1L);
        version.setCompileStatus("SUCCESS");
        version.setSourceCode("package com.kob.test; class Bot {}");
        version.setParentVersionId(5L);
        when(versionRepo.findById(10L)).thenReturn(version);
        when(taskRepo.findById(1L)).thenReturn(task(1L, 7, "COMPLETED"));
        when(toolRouter.evaluate(any(), any(), any())).thenReturn(publicAggregate());

        AgentVersionDetailDto dto = service.getVersionDetail(10L);

        assertEquals("package com.kob.test; class Bot {}", dto.getSourceCode());
        assertEquals(Long.valueOf(5L), dto.getParentVersionId());
        // 单版本详情也带公开评测指标
        assertEquals(Integer.valueOf(48), dto.getPublicGameCount());
        assertEquals(Double.valueOf(0.5), dto.getPublicWinRate());
    }

    @Test
    void getTaskDetailExposesRepresentativeRunsAtTerminal() {
        AgentTask task = task(1L, 7, "COMPLETED");
        task.setBestVersionId(10L);
        BotVersion best = new BotVersion();
        best.setId(10L);
        best.setTaskId(1L);
        best.setCompileStatus("SUCCESS");
        when(taskRepo.findById(1L)).thenReturn(task);
        when(versionRepo.findByTask(1L)).thenReturn(Arrays.asList(best));
        when(versionRepo.findById(10L)).thenReturn(best);
        when(stepRepo.findByTask(1L)).thenReturn(Collections.emptyList());
        when(toolRouter.evaluate(any(), any(), any())).thenReturn(publicAggregate());
        EvaluationRun win = run(20L, 10L, "PUBLIC", "greedy", "A", "WIN", 50, null);
        EvaluationRun loss = run(21L, 10L, "HIDDEN", "territory", "B", "LOSS", 30, "WALL");
        when(runRepo.findByVersionAndDataset(10L, "PUBLIC")).thenReturn(Arrays.asList(win));
        when(runRepo.findByVersionAndDataset(10L, "HIDDEN")).thenReturn(Arrays.asList(loss));

        AgentTaskDetailDto dto = service.getTaskDetail(1L);

        assertNotNull(dto.getRepresentativeRuns());
        assertEquals(2, dto.getRepresentativeRuns().size());
        AgentRunSummaryDto first = dto.getRepresentativeRuns().get(0);
        assertEquals("WIN", first.getResult());
        assertEquals("greedy", first.getOpponentKey());
        assertEquals(Integer.valueOf(50), first.getRounds());
        // 摘要不含地图种子与移动序列：DTO 无 mapSeed/replay 字段，仅校验可读字段
        AgentRunSummaryDto second = dto.getRepresentativeRuns().get(1);
        assertEquals("HIDDEN", second.getDatasetType());
        assertEquals("LOSS", second.getResult());
    }

    @Test
    void getTaskDetailHidesRepresentativeRunsAndHiddenEvalWhileRunning() {
        AgentTask task = task(1L, 7, "EVALUATING");
        when(taskRepo.findById(1L)).thenReturn(task);
        when(versionRepo.findByTask(1L)).thenReturn(Collections.emptyList());
        when(stepRepo.findByTask(1L)).thenReturn(Collections.emptyList());
        when(toolRouter.evaluate(any(), any(), any())).thenReturn(publicAggregate());

        AgentTaskDetailDto dto = service.getTaskDetail(1L);

        assertNull(dto.getRepresentativeRuns());
        assertNull(dto.getHiddenEvaluation());
    }
}
