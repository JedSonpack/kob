package com.kob.backend.agent.service.impl;

import com.kob.backend.agent.dto.AgentRunSummaryDto;
import com.kob.backend.agent.dto.AgentTaskDetailDto;
import com.kob.backend.agent.dto.AgentTaskListItemDto;
import com.kob.backend.agent.dto.AgentVersionDetailDto;
import com.kob.backend.agent.dto.CreateAgentTaskRequest;
import com.kob.backend.agent.dto.SaveAgentVersionRequest;
import com.kob.backend.agent.model.AgentStep;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.model.AgentTaskStatus;
import com.kob.backend.agent.model.BotVersion;
import com.kob.backend.agent.model.DatasetType;
import com.kob.backend.agent.model.EvaluationRun;
import com.kob.backend.agent.repository.AgentStepRepository;
import com.kob.backend.agent.repository.AgentTaskRepository;
import com.kob.backend.agent.repository.BotVersionRepository;
import com.kob.backend.agent.repository.EvaluationRunRepository;
import com.kob.backend.agent.service.AgentTaskService;
import com.kob.backend.agent.tool.AgentToolRouter;
import com.kob.backend.agent.tool.EvaluationAggregate;
import com.kob.backend.agent.workflow.AgentWorkflowExecutor;
import com.kob.backend.agent.workflow.AgentWorkflowService;
import com.kob.backend.pojo.User;
import com.kob.backend.service.impl.utils.UserDetailsImpl;
import com.kob.backend.service.user.bot.AddService;
import com.kob.game.core.DeterministicMapGenerator;
import com.kob.game.core.GameConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 任务用户服务实现。所有查询/操作均校验当前用户所有权；隐藏聚合仅终态返回。
 */
@Service
public class AgentTaskServiceImpl implements AgentTaskService {

    private final AgentTaskRepository taskRepository;
    private final BotVersionRepository versionRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final AgentStepRepository stepRepository;
    private final AgentToolRouter toolRouter;
    private final AgentWorkflowExecutor executor;
    private final AgentWorkflowService workflowService;
    private final AddService addService;

    @Autowired
    public AgentTaskServiceImpl(AgentTaskRepository taskRepository,
                                BotVersionRepository versionRepository,
                                EvaluationRunRepository evaluationRunRepository,
                                AgentStepRepository stepRepository,
                                AgentToolRouter toolRouter,
                                AgentWorkflowExecutor executor,
                                AgentWorkflowService workflowService,
                                AddService addService) {
        this.taskRepository = taskRepository;
        this.versionRepository = versionRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.stepRepository = stepRepository;
        this.toolRouter = toolRouter;
        this.executor = executor;
        this.workflowService = workflowService;
        this.addService = addService;
    }

    @Override
    public Long createTask(CreateAgentTaskRequest request) {
        User user = currentUser();
        String goal = request.getGoal();
        if (goal == null || goal.trim().isEmpty()) {
            throw new AgentTaskException("策略目标不能为空");
        }
        if (goal.length() > 1000) {
            throw new AgentTaskException("策略目标不能超过 1000 字符");
        }
        Integer max = request.getMaxIterations();
        if (max == null || max < 1 || max > 3) {
            throw new AgentTaskException("最大迭代次数必须在 1..3");
        }
        for (AgentTask t : taskRepository.findByUser(user.getId())) {
            if (t.getActiveSlot() != null && t.getActiveSlot() == 1) {
                throw new AgentTaskConflictException("已有运行中的 Agent 任务");
            }
        }
        AgentTask task = new AgentTask();
        task.setUserId(user.getId());
        task.setGoal(goal);
        task.setStatus("CREATED");
        task.setCurrentIteration(0);
        task.setMaxIterations(max);
        task.setActiveSlot(1);
        task.setVersion(0);
        task.setCreatedAt(new Date());
        task.setUpdatedAt(new Date());
        taskRepository.insert(task);
        executor.submit(task.getId());
        return task.getId();
    }

    @Override
    public List<AgentTaskListItemDto> listTasks() {
        User user = currentUser();
        return taskRepository.findByUser(user.getId()).stream()
                .map(AgentTaskListItemDto::new)
                .collect(Collectors.toList());
    }

    @Override
    public AgentTaskDetailDto getTaskDetail(Long taskId) {
        User user = currentUser();
        AgentTask task = ownedTask(taskId, user.getId());
        AgentTaskDetailDto dto = new AgentTaskDetailDto(task);
        List<BotVersion> versions = versionRepository.findByTask(taskId);
        List<AgentVersionDetailDto> versionDtos = new ArrayList<>();
        for (BotVersion v : versions) {
            if (!"SUCCESS".equals(v.getCompileStatus())) {
                continue;
            }
            EvaluationAggregate publicEval = safePublicEval(task, v);
            versionDtos.add(new AgentVersionDetailDto(v, publicEval));
            if (versionDtos.size() >= 3) {
                break;
            }
        }
        dto.setVersions(versionDtos);
        dto.setSteps(stepRepository.findByTask(taskId));
        dto.setPublicEvaluation(bestPublicEval(task, versions));
        if (AgentTaskStatus.valueOf(task.getStatus()).isTerminal()) {
            dto.setHiddenEvaluation(hiddenAggregate(task, task.getBestVersionId()));
            dto.setRepresentativeRuns(representativeRuns(task.getBestVersionId()));
        }
        return dto;
    }

    @Override
    public void cancelTask(Long taskId) {
        User user = currentUser();
        workflowService.cancelTask(taskId, user.getId());
    }

    @Override
    public AgentVersionDetailDto getVersionDetail(Long versionId) {
        User user = currentUser();
        BotVersion version = versionRepository.findById(versionId);
        if (version == null) {
            throw new AgentTaskNotFoundException("版本不存在");
        }
        AgentTask task = taskRepository.findById(version.getTaskId());
        if (task == null || !task.getUserId().equals(user.getId())) {
            throw new AgentTaskNotFoundException("版本不存在");
        }
        AgentVersionDetailDto dto = new AgentVersionDetailDto(version, safePublicEval(task, version));
        // 单版本详情接口暴露源码与父版本，供前端代码对比；任务详情的版本列表不返回这两项。
        dto.setSourceCode(version.getSourceCode());
        dto.setParentVersionId(version.getParentVersionId());
        return dto;
    }

    @Override
    public Map<String, Object> getReplay(Long runId) {
        User user = currentUser();
        EvaluationRun run = evaluationRunRepository.findById(runId);
        if (run == null) {
            throw new AgentTaskNotFoundException("录像不存在");
        }
        BotVersion version = versionRepository.findById(run.getVersionId());
        AgentTask task = version == null ? null : taskRepository.findById(version.getTaskId());
        if (task == null || !task.getUserId().equals(user.getId())) {
            throw new AgentTaskNotFoundException("录像不存在");
        }
        if ("HIDDEN".equals(run.getDatasetType())
                && !AgentTaskStatus.valueOf(task.getStatus()).isTerminal()) {
            throw new AgentTaskConflictException("隐藏集录像仅任务终态可查看");
        }
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> record = new HashMap<>();
        int[][] map = new DeterministicMapGenerator().generate(new GameConfig(13, 14, 20, run.getMapSeed(), 1000));
        record.put("map", mapString(map));
        record.put("aid", 0);
        record.put("asx", 11);
        record.put("asy", 1);
        record.put("bid", 1);
        record.put("bsx", 1);
        record.put("bsy", 12);
        String[] steps = splitReplay(run.getReplay());
        record.put("asteps", steps[0]);
        record.put("bsteps", steps[1]);
        record.put("loser", loserOf(run.getResult(), run.getSide()));
        result.put("record", record);
        result.put("opponentKey", run.getOpponentKey());
        result.put("side", run.getSide());
        result.put("failureReason", run.getFailureReason());
        return result;
    }

    @Override
    public Map<String, String> saveVersion(Long versionId, SaveAgentVersionRequest request) {
        User user = currentUser();
        BotVersion version = versionRepository.findById(versionId);
        if (version == null) {
            throw new AgentTaskNotFoundException("版本不存在");
        }
        AgentTask task = taskRepository.findById(version.getTaskId());
        if (task == null || !task.getUserId().equals(user.getId())) {
            throw new AgentTaskNotFoundException("版本不存在");
        }
        if (!"SUCCESS".equals(version.getCompileStatus())
                || !AgentTaskStatus.valueOf(task.getStatus()).isTerminal()) {
            throw new AgentTaskConflictException("仅编译成功且任务终态的版本可保存为 Bot");
        }
        Map<String, String> data = new HashMap<>();
        data.put("title", request.getTitle());
        data.put("description", version.getStrategySummary());
        data.put("content", version.getSourceCode());
        return addService.add(data);
    }

    private EvaluationAggregate safePublicEval(AgentTask task, BotVersion version) {
        try {
            return toolRouter.evaluate(task, version, DatasetType.PUBLIC);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private EvaluationAggregate bestPublicEval(AgentTask task, List<BotVersion> versions) {
        for (BotVersion v : versions) {
            if (task.getBestVersionId() != null && task.getBestVersionId().equals(v.getId())) {
                return safePublicEval(task, v);
            }
        }
        return null;
    }

    private EvaluationAggregate hiddenAggregate(AgentTask task, Long versionId) {
        if (versionId == null) {
            return null;
        }
        BotVersion version = versionRepository.findById(versionId);
        if (version == null) {
            return null;
        }
        try {
            return toolRouter.evaluate(task, version, DatasetType.HIDDEN);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 最佳版本的代表性录像摘要（不含地图种子与移动序列）。终态才调用，故公开+隐藏集都可暴露。
     */
    private List<AgentRunSummaryDto> representativeRuns(Long versionId) {
        if (versionId == null) {
            return Collections.emptyList();
        }
        List<AgentRunSummaryDto> summaries = new ArrayList<>();
        for (EvaluationRun run : evaluationRunRepository.findByVersionAndDataset(versionId, "PUBLIC")) {
            summaries.add(toRunSummary(run));
        }
        for (EvaluationRun run : evaluationRunRepository.findByVersionAndDataset(versionId, "HIDDEN")) {
            summaries.add(toRunSummary(run));
        }
        return summaries;
    }

    private static AgentRunSummaryDto toRunSummary(EvaluationRun run) {
        return new AgentRunSummaryDto(run.getId(), run.getVersionId(), run.getOpponentKey(),
                run.getSide(), run.getResult(), run.getRounds(), run.getDecisionP95Ms(),
                run.getFailureReason(), run.getDatasetType());
    }

    private AgentTask ownedTask(Long taskId, Integer userId) {
        AgentTask task = taskRepository.findById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new AgentTaskNotFoundException("任务不存在");
        }
        return task;
    }

    private User currentUser() {
        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        return userDetails.getUser();
    }

    private static String mapString(int[][] map) {
        StringBuilder sb = new StringBuilder();
        for (int[] row : map) {
            for (int c : row) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String[] splitReplay(String replay) {
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        if (replay != null) {
            for (String frame : replay.split(";")) {
                String[] parts = frame.split(",");
                if (parts.length >= 2) {
                    a.append(parts[0]);
                    b.append(parts[1]);
                }
            }
        }
        return new String[]{a.toString(), b.toString()};
    }

    private static String loserOf(String result, String side) {
        if ("DRAW".equals(result)) {
            return "all";
        }
        boolean candidateWon = "WIN".equals(result);
        boolean candidateIsA = "A".equals(side);
        if (candidateWon) {
            return candidateIsA ? "B" : "A";
        }
        return candidateIsA ? "A" : "B";
    }
}
