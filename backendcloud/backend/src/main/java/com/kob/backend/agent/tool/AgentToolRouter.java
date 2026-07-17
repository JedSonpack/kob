package com.kob.backend.agent.tool;

import com.kob.backend.agent.model.AgentStep;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.model.BotVersion;
import com.kob.backend.agent.model.DatasetType;
import com.kob.backend.agent.model.EvaluationRun;
import com.kob.backend.agent.repository.AgentStepRepository;
import com.kob.backend.agent.repository.BotVersionRepository;
import com.kob.backend.agent.repository.EvaluationRunRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具路由：以持久 agent_step 约束编译与评测调用，幂等复用，按状态校验越权。
 *
 * <ul>
 *   <li>compile：COMPILE_ONLY 模式编译；已有成功 Step 时从 bot_version.compile_status 复用。</li>
 *   <li>evaluate：PUBLIC/HIDDEN 模式评测；已有成功 Step 时从 evaluation_run 重新聚合。</li>
 *   <li>cancel：取消对应 requestId 的远端评测。</li>
 * </ul>
 */
@Component
public class AgentToolRouter {

    private static final String COMPILE_ONLY = "COMPILE_ONLY";

    private final EvaluationClient client;
    private final BotVersionRepository versionRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final AgentStepRepository stepRepository;

    public AgentToolRouter(EvaluationClient client, BotVersionRepository versionRepository,
                           EvaluationRunRepository evaluationRunRepository,
                           AgentStepRepository stepRepository) {
        this.client = client;
        this.versionRepository = versionRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.stepRepository = stepRepository;
    }

    public CompileToolResult compile(AgentTask task, BotVersion version) {
        String key = idempotencyKey(task.getId(), version.getId(), "compile");
        AgentStep existing = stepRepository.findByIdempotencyKey(key);
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            return new CompileToolResult("SUCCESS".equals(version.getCompileStatus()), version.getCompileError());
        }
        AgentStep step = stepRepository.insertRunning(task.getId(), task.getStatus(), "compile", key, "version:" + version.getId());
        long start = System.currentTimeMillis();
        try {
            EvaluationResponse response = client.evaluate(new EvaluationRequest(
                    requestId(task.getId(), version.getId(), COMPILE_ONLY),
                    version.getSourceCode(), COMPILE_ONLY));
            boolean succeeded = response.isCompileSucceeded();
            String error = response.getCompileError();
            versionRepository.updateCompileStatus(version.getId(), succeeded ? "SUCCESS" : "FAILED", error);
            version.setCompileStatus(succeeded ? "SUCCESS" : "FAILED");
            version.setCompileError(error);
            stepRepository.markSuccess(step.getId(), System.currentTimeMillis() - start,
                    "compile:" + succeeded, null, null);
            return new CompileToolResult(succeeded, error);
        } catch (RuntimeException e) {
            stepRepository.markFailed(step.getId(), System.currentTimeMillis() - start,
                    "EVALUATION_TIMEOUT", e.getMessage());
            throw e;
        }
    }

    public EvaluationAggregate evaluate(AgentTask task, BotVersion version, DatasetType dataset) {
        if (dataset == DatasetType.PUBLIC && "COMPILING".equals(task.getStatus())) {
            throw new IllegalStateException("COMPILING 状态不允许公开评测");
        }
        if (dataset == DatasetType.HIDDEN && "EVALUATING".equals(task.getStatus())) {
            throw new IllegalStateException("EVALUATING 状态不允许隐藏评测");
        }
        String toolName = "evaluate_" + dataset.name();
        String key = idempotencyKey(task.getId(), version.getId(), toolName);
        AgentStep existing = stepRepository.findByIdempotencyKey(key);
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            return aggregateFromRuns(version.getId(), dataset);
        }
        AgentStep step = stepRepository.insertRunning(task.getId(), task.getStatus(), toolName, key, "version:" + version.getId());
        long start = System.currentTimeMillis();
        try {
            EvaluationResponse response = client.evaluate(new EvaluationRequest(
                    requestId(task.getId(), version.getId(), dataset.name()),
                    version.getSourceCode(), dataset.name()));
            if (response.getMatches() != null) {
                for (EvaluationMatchResult m : response.getMatches()) {
                    saveRun(version.getId(), dataset, m);
                }
            }
            EvaluationAggregate aggregate = aggregateFromSummary(dataset, response.getSummary());
            int gameCount = response.getSummary() == null ? 0 : response.getSummary().getGameCount();
            stepRepository.markSuccess(step.getId(), System.currentTimeMillis() - start,
                    "evaluate:" + gameCount, null, null);
            return aggregate;
        } catch (RuntimeException e) {
            stepRepository.markFailed(step.getId(), System.currentTimeMillis() - start,
                    "EVALUATION_TIMEOUT", e.getMessage());
            throw e;
        }
    }

    public void cancel(Long taskId, Long versionId, DatasetType dataset) {
        client.cancel(requestId(taskId, versionId, dataset.name()));
    }

    private void saveRun(Long versionId, DatasetType dataset, EvaluationMatchResult m) {
        EvaluationRun run = new EvaluationRun();
        run.setVersionId(versionId);
        run.setDatasetType(dataset.name());
        run.setOpponentKey(m.getOpponentKey());
        run.setMapSeed(m.getMapSeed());
        run.setSide(m.getSide());
        run.setResult(m.getResult());
        run.setRounds(m.getRounds());
        run.setDecisionP95Ms(m.getDecisionP95Ms());
        run.setInvalidMoveCount(m.getInvalidMoveCount());
        run.setFailureReason(m.getFailureReason());
        run.setReplay(m.getReplay());
        evaluationRunRepository.saveIfAbsent(run);
    }

    private EvaluationAggregate aggregateFromSummary(DatasetType dataset, EvaluationSummary s) {
        if (s == null) {
            return new EvaluationAggregate(dataset, 0, 0, 0, 0, 0, 0, new HashMap<String, Integer>());
        }
        return new EvaluationAggregate(dataset, s.getGameCount(), s.getScore(), s.getWinRate(),
                s.getAverageRounds(), s.getDecisionP95Ms(), s.getInvalidMoveCount(), s.getFailureCounts());
    }

    private EvaluationAggregate aggregateFromRuns(Long versionId, DatasetType dataset) {
        List<EvaluationRun> runs = evaluationRunRepository.findByVersionAndDataset(versionId, dataset.name());
        int wins = 0, draws = 0, invalid = 0;
        long totalRounds = 0;
        long maxP95 = 0;
        Map<String, Integer> failureCounts = new HashMap<>();
        for (EvaluationRun r : runs) {
            if ("WIN".equals(r.getResult())) wins++;
            else if ("DRAW".equals(r.getResult())) draws++;
            totalRounds += r.getRounds();
            invalid += r.getInvalidMoveCount();
            if (r.getDecisionP95Ms() > maxP95) maxP95 = r.getDecisionP95Ms();
            if (r.getFailureReason() != null) {
                failureCounts.merge(r.getFailureReason(), 1, Integer::sum);
            }
        }
        int n = runs.size();
        double score = n == 0 ? 0 : (wins + 0.5 * draws) / n;
        double winRate = n == 0 ? 0 : (double) wins / n;
        double avgRounds = n == 0 ? 0 : (double) totalRounds / n;
        return new EvaluationAggregate(dataset, n, score, winRate, avgRounds, maxP95, invalid, failureCounts);
    }

    private static String requestId(Long taskId, Long versionId, String mode) {
        return taskId + ":" + versionId + ":" + mode;
    }

    private static String idempotencyKey(Long taskId, Long versionId, String toolName) {
        return "task:" + taskId + ":version:" + versionId + ":tool:" + toolName;
    }
}
