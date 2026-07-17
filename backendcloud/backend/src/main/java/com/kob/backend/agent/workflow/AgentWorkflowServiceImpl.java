package com.kob.backend.agent.workflow;

import com.kob.backend.agent.llm.LlmContext;
import com.kob.backend.agent.llm.LlmDecision;
import com.kob.backend.agent.llm.LlmStepExecutor;
import com.kob.backend.agent.llm.LlmStepResult;
import com.kob.backend.agent.model.AgentAction;
import com.kob.backend.agent.model.AgentErrorCode;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.model.AgentTaskStatus;
import com.kob.backend.agent.model.BotVersion;
import com.kob.backend.agent.model.DatasetType;
import com.kob.backend.agent.model.EvaluationRun;
import com.kob.backend.agent.repository.AgentTaskRepository;
import com.kob.backend.agent.repository.BotVersionRepository;
import com.kob.backend.agent.repository.EvaluationRunRepository;
import com.kob.backend.agent.tool.AgentToolRouter;
import com.kob.backend.agent.tool.CompileToolResult;
import com.kob.backend.agent.tool.EvaluationAggregate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工作流状态机（设计规格 8.1）。
 *
 * <p>每次循环重新读取任务并按状态推进；CAS 失败不抛错，下一轮重读。工具调用幂等，
 * 故重派安全。隐藏集仅在 VALIDATING 运行，结果不进入模型上下文。
 */
@Service
public class AgentWorkflowServiceImpl implements AgentWorkflowService {

    private static final int MAX_LOOP = 2000;

    private final AgentTaskRepository taskRepository;
    private final BotVersionRepository versionRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final LlmStepExecutor llmStepExecutor;
    private final AgentToolRouter toolRouter;
    private final BestVersionSelector bestVersionSelector;
    private final long maxP95Ms;
    private AgentWorkflowExecutor executor;

    public AgentWorkflowServiceImpl(AgentTaskRepository taskRepository,
                                    BotVersionRepository versionRepository,
                                    EvaluationRunRepository evaluationRunRepository,
                                    LlmStepExecutor llmStepExecutor,
                                    AgentToolRouter toolRouter,
                                    BestVersionSelector bestVersionSelector,
                                    @Value("${kob.agent.evaluation.max-p95-ms:100}") long maxP95Ms) {
        this.taskRepository = taskRepository;
        this.versionRepository = versionRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.llmStepExecutor = llmStepExecutor;
        this.toolRouter = toolRouter;
        this.bestVersionSelector = bestVersionSelector;
        this.maxP95Ms = maxP95Ms;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAgentWorkflowExecutor(AgentWorkflowExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void runTask(Long taskId) {
        for (int i = 0; i < MAX_LOOP; i++) {
            AgentTask task = taskRepository.findById(taskId);
            if (task == null) {
                return;
            }
            AgentTaskStatus status = AgentTaskStatus.valueOf(task.getStatus());
            if (status.isTerminal()) {
                return;
            }
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            try {
                dispatch(task, status);
            } catch (RuntimeException e) {
                failTask(task, e);
                return;
            }
        }
    }

    @Override
    public void resumeIncompleteTasks() {
        if (executor == null) {
            return;
        }
        for (AgentTask task : taskRepository.findIncompleteTasks()) {
            executor.submit(task.getId());
        }
    }

    @Override
    public void cancelTask(Long taskId, Integer userId) {
        AgentTask task = taskRepository.findById(taskId);
        if (task == null || userId != null && !userId.equals(task.getUserId())) {
            return;
        }
        if (AgentTaskStatus.valueOf(task.getStatus()).isTerminal()) {
            return;
        }
        BotVersion current = findCurrentVersion(task);
        if (current != null) {
            toolRouter.cancel(taskId, current.getId(), DatasetType.PUBLIC);
        }
        taskRepository.transition(task, AgentTaskStatus.CANCELLED, null, null,
                AgentErrorCode.TASK_CANCELLED, null, true);
        if (executor != null) {
            executor.cancel(taskId);
        }
    }

    private void dispatch(AgentTask task, AgentTaskStatus status) {
        switch (status) {
            case CREATED:
                moveToGenerating(task);
                break;
            case GENERATING:
                generateFirstVersion(task);
                break;
            case COMPILING:
                compileCurrentVersion(task);
                break;
            case REPAIRING:
                repairCurrentVersion(task);
                break;
            case EVALUATING:
                evaluateCurrentVersion(task);
                break;
            case ANALYZING:
                decideNextAction(task);
                break;
            case IMPROVING:
                movePreparedVersionToCompiling(task);
                break;
            case VALIDATING:
                validateCandidates(task);
                break;
            default:
                break;
        }
    }

    private void moveToGenerating(AgentTask task) {
        taskRepository.transition(task, AgentTaskStatus.GENERATING, null, null, null, null, false);
    }

    private void generateFirstVersion(AgentTask task) {
        LlmContext context = buildContext(task, AgentTaskStatus.GENERATING, null, null, null);
        LlmStepResult result = llmStepExecutor.execute(task, context, 1, 1, null);
        if (result.getDecision().getAction() != AgentAction.GENERATE_CODE) {
            throw new IllegalStateException("GENERATING 期望 GENERATE_CODE");
        }
        taskRepository.transition(task, AgentTaskStatus.COMPILING, 1, null, null, null, false);
    }

    private void compileCurrentVersion(AgentTask task) {
        BotVersion version = findCurrentVersion(task);
        if (version == null) {
            throw new IllegalStateException("COMPILING 找不到当前版本");
        }
        CompileToolResult result = toolRouter.compile(task, version);
        if (result.isSucceeded()) {
            taskRepository.transition(task, AgentTaskStatus.EVALUATING, null, null, null, null, false);
        } else if (version.getAttempt() == 1) {
            taskRepository.transition(task, AgentTaskStatus.REPAIRING, null, null, null, null, false);
        } else {
            taskRepository.transition(task, AgentTaskStatus.FAILED, null, null,
                    AgentErrorCode.COMPILE_FAILED, result.getCompileError(), true);
        }
    }

    private void repairCurrentVersion(AgentTask task) {
        BotVersion failed = findCurrentVersion(task);
        LlmContext context = buildContext(task, AgentTaskStatus.REPAIRING, failed, null, failed.getCompileError());
        Long parentId = failed == null ? null : failed.getId();
        LlmStepResult result = llmStepExecutor.execute(task, context, task.getCurrentIteration(), 2, parentId);
        if (result.getDecision().getAction() != AgentAction.REPAIR_CODE) {
            throw new IllegalStateException("REPAIRING 期望 REPAIR_CODE");
        }
        taskRepository.transition(task, AgentTaskStatus.COMPILING, null, null, null, null, false);
    }

    private void evaluateCurrentVersion(AgentTask task) {
        BotVersion version = findCurrentVersion(task);
        if (version == null) {
            throw new IllegalStateException("EVALUATING 找不到当前版本");
        }
        toolRouter.evaluate(task, version, DatasetType.PUBLIC);
        taskRepository.transition(task, AgentTaskStatus.ANALYZING, null, null, null, null, false);
    }

    private void decideNextAction(AgentTask task) {
        BotVersion version = findCurrentVersion(task);
        EvaluationAggregate publicEval = toolRouter.evaluate(task, version, DatasetType.PUBLIC);
        LlmContext context = buildContext(task, AgentTaskStatus.ANALYZING, version, publicEval, null);
        Long parentId = version == null ? null : version.getId();
        LlmStepResult result = llmStepExecutor.execute(task, context,
                (task.getCurrentIteration() == null ? 0 : task.getCurrentIteration()) + 1, 1, parentId);
        LlmDecision decision = result.getDecision();
        if (decision.getAction() == AgentAction.FINISH) {
            taskRepository.transition(task, AgentTaskStatus.VALIDATING, null, null, null, null, false);
        } else if (decision.getAction() == AgentAction.IMPROVE_CODE) {
            taskRepository.transition(task, AgentTaskStatus.IMPROVING,
                    task.getCurrentIteration() + 1, null, null, null, false);
        } else {
            throw new IllegalStateException("ANALYZING 不允许的动作: " + decision.getAction());
        }
    }

    private void movePreparedVersionToCompiling(AgentTask task) {
        BotVersion next = findCurrentVersion(task);
        if (next == null) {
            throw new IllegalStateException("IMPROVING 找不到新版本");
        }
        taskRepository.transition(task, AgentTaskStatus.COMPILING, null, null, null, null, false);
    }

    private void validateCandidates(AgentTask task) {
        List<BotVersion> versions = versionRepository.findByTask(task.getId());
        List<BestVersionSelector.Candidate> candidates = new ArrayList<>();
        List<BotVersion> qualified = new ArrayList<>();
        for (BotVersion v : versions) {
            if (!"SUCCESS".equals(v.getCompileStatus())) {
                continue;
            }
            EvaluationAggregate publicEval = toolRouter.evaluate(task, v, DatasetType.PUBLIC);
            if (publicEval.getInvalidMoveCount() != 0 || publicEval.getDecisionP95Ms() > maxP95Ms) {
                continue;
            }
            qualified.add(v);
        }
        if (qualified.isEmpty()) {
            taskRepository.transition(task, AgentTaskStatus.FAILED, null, null,
                    AgentErrorCode.INVALID_MOVE_RATE, "无合格候选版本", true);
            return;
        }
        for (BotVersion v : qualified) {
            EvaluationAggregate publicEval = toolRouter.evaluate(task, v, DatasetType.PUBLIC);
            EvaluationAggregate hiddenEval = toolRouter.evaluate(task, v, DatasetType.HIDDEN);
            candidates.add(BestVersionSelector.candidate(v, publicEval.getScore(),
                    publicEval.getDecisionP95Ms(), hiddenEval.getScore()));
        }
        Long bestId = bestVersionSelector.select(candidates);
        for (BotVersion v : qualified) {
            versionRepository.markAccepted(v.getId(), v.getId().equals(bestId));
        }
        taskRepository.transition(task, AgentTaskStatus.COMPLETED, null, bestId, null, null, true);
    }

    private void failTask(AgentTask task, RuntimeException e) {
        AgentErrorCode code = AgentErrorCode.EVALUATION_TIMEOUT;
        Throwable cause = e.getCause();
        if (cause instanceof java.util.concurrent.TimeoutException) {
            code = AgentErrorCode.LLM_TIMEOUT;
        }
        taskRepository.transition(task, AgentTaskStatus.FAILED, null, null, code, e.getMessage(), true);
    }

    private BotVersion findCurrentVersion(AgentTask task) {
        List<BotVersion> versions = versionRepository.findByTask(task.getId());
        BotVersion current = null;
        for (BotVersion v : versions) {
            if (task.getCurrentIteration() != null && v.getIteration() == task.getCurrentIteration()) {
                if (current == null || v.getAttempt() > current.getAttempt()) {
                    current = v;
                }
            }
        }
        return current;
    }

    private LlmContext buildContext(AgentTask task, AgentTaskStatus status, BotVersion version,
                                    EvaluationAggregate publicEval, String compileError) {
        String currentSource = version == null ? null : version.getSourceCode();
        List<String> failureSummaries = version == null
                ? Collections.<String>emptyList()
                : failureSummaries(version.getId());
        String previousChange = previousChangeSummary(task, version);
        return new LlmContext(task.getId(), status, task.getGoal(),
                task.getCurrentIteration() == null ? 0 : task.getCurrentIteration(),
                task.getMaxIterations(), currentSource, compileError, publicEval,
                failureSummaries, previousChange);
    }

    private List<String> failureSummaries(Long versionId) {
        List<EvaluationRun> runs = evaluationRunRepository.findByVersionAndDataset(versionId, "PUBLIC");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (EvaluationRun r : runs) {
            if (r.getFailureReason() != null && !"WIN".equals(r.getResult())) {
                counts.merge(r.getFailureReason(), 1, Integer::sum);
            }
        }
        List<String> summaries = new ArrayList<>();
        counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(3)
                .forEach(e -> summaries.add(e.getKey() + ":" + e.getValue()));
        return summaries;
    }

    private String previousChangeSummary(AgentTask task, BotVersion version) {
        if (version == null || version.getParentVersionId() == null) {
            return null;
        }
        BotVersion parent = versionRepository.findById(version.getParentVersionId());
        return parent == null ? null : parent.getChangeReason();
    }
}
