package com.kob.backend.agent.workflow;

import com.kob.backend.agent.model.AgentErrorCode;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 内存版 Agent 仓库集合，供工作流测试使用（绕过 DB 与 mapper）。
 */
public class InMemoryAgentRepositories {

    public final InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    public final InMemoryBotVersionRepository versionRepository = new InMemoryBotVersionRepository();
    public final InMemoryEvaluationRunRepository evaluationRunRepository = new InMemoryEvaluationRunRepository();
    public final InMemoryAgentStepRepository stepRepository = new InMemoryAgentStepRepository();

    public static class InMemoryTaskRepository extends AgentTaskRepository {
        private final Map<Long, AgentTask> db = new LinkedHashMap<>();
        private final AtomicLong ids = new AtomicLong(0);

        public InMemoryTaskRepository() { super(null); }

        public void seed(AgentTask task) {
            if (task.getId() == null) task.setId(ids.incrementAndGet());
            db.put(task.getId(), task);
        }

        @Override public AgentTask findById(Long id) { return db.get(id); }

        @Override public boolean transition(AgentTask task, AgentTaskStatus target,
                                            Integer currentIteration, Long bestVersionId,
                                            AgentErrorCode errorCode, String errorMessage,
                                            boolean clearActiveSlot) {
            AgentTask t = db.get(task.getId());
            if (t == null || !t.getVersion().equals(task.getVersion())
                    || !t.getStatus().equals(task.getStatus())) {
                return false;
            }
            t.setStatus(target.name());
            t.setVersion(t.getVersion() + 1);
            if (currentIteration != null) t.setCurrentIteration(currentIteration);
            if (bestVersionId != null) t.setBestVersionId(bestVersionId);
            if (errorCode != null) t.setErrorCode(errorCode.name());
            if (errorMessage != null) t.setErrorMessage(errorMessage);
            if (clearActiveSlot) t.setActiveSlot(null);
            task.setStatus(t.getStatus());
            task.setVersion(t.getVersion());
            task.setCurrentIteration(t.getCurrentIteration());
            task.setBestVersionId(t.getBestVersionId());
            task.setActiveSlot(t.getActiveSlot());
            task.setErrorCode(t.getErrorCode());
            task.setErrorMessage(t.getErrorMessage());
            return true;
        }

        @Override public List<AgentTask> findIncompleteTasks() {
            return db.values().stream()
                    .filter(t -> !AgentTaskStatus.valueOf(t.getStatus()).isTerminal())
                    .collect(Collectors.toList());
        }

        @Override public void insert(AgentTask task) { seed(task); }
    }

    public static class InMemoryBotVersionRepository extends BotVersionRepository {
        private final Map<Long, BotVersion> db = new LinkedHashMap<>();
        private final AtomicLong ids = new AtomicLong(0);

        public InMemoryBotVersionRepository() { super(null, null); }

        @Override public BotVersion save(BotVersion version) {
            if (version.getId() == null) version.setId(ids.incrementAndGet());
            if (version.getCreatedAt() == null) version.setCreatedAt(new Date());
            db.put(version.getId(), version);
            return version;
        }

        @Override public BotVersion findById(Long id) { return db.get(id); }

        @Override public List<BotVersion> findByTask(Long taskId) {
            return db.values().stream()
                    .filter(v -> v.getTaskId().equals(taskId))
                    .collect(Collectors.toList());
        }

        @Override public boolean updateCompileStatus(Long versionId, String compileStatus, String compileError) {
            BotVersion v = db.get(versionId);
            if (v == null) return false;
            v.setCompileStatus(compileStatus);
            v.setCompileError(compileError);
            return true;
        }

        @Override public boolean markAccepted(Long versionId, boolean accepted) {
            BotVersion v = db.get(versionId);
            if (v == null) return false;
            v.setAccepted(accepted ? 1 : 0);
            return true;
        }
    }

    public static class InMemoryEvaluationRunRepository extends EvaluationRunRepository {
        private final Map<Long, EvaluationRun> db = new LinkedHashMap<>();
        private final AtomicLong ids = new AtomicLong(0);

        public InMemoryEvaluationRunRepository() { super(null); }

        @Override public void saveIfAbsent(EvaluationRun run) {
            for (EvaluationRun r : db.values()) {
                if (r.getVersionId().equals(run.getVersionId())
                        && r.getDatasetType().equals(run.getDatasetType())
                        && r.getOpponentKey().equals(run.getOpponentKey())
                        && r.getMapSeed() == run.getMapSeed()
                        && r.getSide().equals(run.getSide())) {
                    return;
                }
            }
            run.setId(ids.incrementAndGet());
            db.put(run.getId(), run);
        }

        @Override public List<EvaluationRun> findByVersionAndDataset(Long versionId, String datasetType) {
            return db.values().stream()
                    .filter(r -> r.getVersionId().equals(versionId)
                            && r.getDatasetType().equals(datasetType))
                    .collect(Collectors.toList());
        }

        @Override public EvaluationRun findById(Long id) { return db.get(id); }
    }

    public static class InMemoryAgentStepRepository extends AgentStepRepository {
        private final Map<Long, AgentStep> db = new LinkedHashMap<>();
        private final Map<String, AgentStep> byKey = new LinkedHashMap<>();
        private final AtomicLong ids = new AtomicLong(0);
        private final AtomicLong seq = new AtomicLong(0);

        public InMemoryAgentStepRepository() { super(null); }

        @Override public AgentStep findByIdempotencyKey(String key) { return byKey.get(key); }

        @Override public int nextSequence(Long taskId) { return (int) seq.incrementAndGet(); }

        @Override public AgentStep insertRunning(Long taskId, String phase, String toolName,
                                                 String idempotencyKey, String inputSummary) {
            AgentStep step = new AgentStep();
            step.setId(ids.incrementAndGet());
            step.setTaskId(taskId);
            step.setSequence(nextSequence(taskId));
            step.setPhase(phase);
            step.setToolName(toolName);
            step.setIdempotencyKey(idempotencyKey);
            step.setInputSummary(inputSummary);
            step.setStatus("RUNNING");
            step.setCreatedAt(new Date());
            db.put(step.getId(), step);
            byKey.put(idempotencyKey, step);
            return step;
        }

        @Override public void markSuccess(Long stepId, long durationMs, String outputSummary,
                                          Integer promptTokens, Integer completionTokens) {
            AgentStep s = db.get(stepId);
            if (s == null) return;
            s.setStatus("SUCCESS");
            s.setDurationMs(durationMs);
            s.setOutputSummary(outputSummary);
            s.setPromptTokens(promptTokens);
            s.setCompletionTokens(completionTokens);
        }

        @Override public void markFailed(Long stepId, long durationMs, String errorCode, String outputSummary) {
            AgentStep s = db.get(stepId);
            if (s == null) return;
            s.setStatus("FAILED");
            s.setDurationMs(durationMs);
            s.setErrorCode(errorCode);
            s.setOutputSummary(outputSummary);
        }

        @Override public List<AgentStep> findByTask(Long taskId) {
            return new ArrayList<>(db.values());
        }
    }

    public DatasetType unused() { return DatasetType.PUBLIC; }
}
