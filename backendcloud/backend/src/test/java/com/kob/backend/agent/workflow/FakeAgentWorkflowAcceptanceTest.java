package com.kob.backend.agent.workflow;

import com.kob.backend.agent.llm.FakeLlmClient;
import com.kob.backend.agent.llm.LlmClient;
import com.kob.backend.agent.llm.LlmContext;
import com.kob.backend.agent.llm.LlmDecision;
import com.kob.backend.agent.llm.LlmDecisionValidator;
import com.kob.backend.agent.llm.LlmStepExecutor;
import com.kob.backend.agent.model.AgentAction;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.model.AgentTaskStatus;
import com.kob.backend.agent.model.BotVersion;
import com.kob.backend.agent.model.DatasetType;
import com.kob.backend.agent.tool.AgentToolRouter;
import com.kob.backend.agent.tool.CompileToolResult;
import com.kob.backend.agent.tool.EvaluationAggregate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fake LLM 端到端验收（阶段 2 任务 9）：真实 WorkflowServiceImpl + FakeLlmClient + 内存仓库 + Fake ToolRouter。
 */
class FakeAgentWorkflowAcceptanceTest {

    private static AgentTask newTask(int maxIterations) {
        AgentTask task = new AgentTask();
        task.setId(1L);
        task.setUserId(7);
        task.setGoal("扩大可活动区域，避免狭窄通道");
        task.setStatus("CREATED");
        task.setCurrentIteration(0);
        task.setMaxIterations(maxIterations);
        task.setActiveSlot(1);
        task.setVersion(0);
        task.setCreatedAt(new Date());
        task.setUpdatedAt(new Date());
        return task;
    }

    /** 可配置 Fake 路由：编译成功/失败、公开评测正常/抛异常、隐藏按版本给分。 */
    private static class FakeRouter extends AgentToolRouter {
        final boolean compileOk;
        final boolean publicEvalThrows;
        final Map<Integer, Double> hiddenByIteration = new HashMap<>();

        FakeRouter(boolean compileOk, boolean publicEvalThrows) {
            super(null, null, null, null);
            this.compileOk = compileOk;
            this.publicEvalThrows = publicEvalThrows;
            hiddenByIteration.put(1, 0.5);
            hiddenByIteration.put(2, 0.6);
            hiddenByIteration.put(3, 0.55);
        }

        @Override
        public CompileToolResult compile(AgentTask task, BotVersion version) {
            version.setCompileStatus(compileOk ? "SUCCESS" : "FAILED");
            return new CompileToolResult(compileOk, compileOk ? null : "编译失败");
        }

        @Override
        public EvaluationAggregate evaluate(AgentTask task, BotVersion version, DatasetType dataset) {
            if (dataset == DatasetType.PUBLIC) {
                if (publicEvalThrows) {
                    throw new RuntimeException("公开评测超时");
                }
                return new EvaluationAggregate(dataset, 48, 0.5, 0.5, 50, 10, 0, new HashMap<String, Integer>());
            }
            double hidden = hiddenByIteration.getOrDefault(version.getIteration(), 0.5);
            return new EvaluationAggregate(dataset, 24, hidden, hidden, 50, 10, 0, new HashMap<String, Integer>());
        }

        @Override
        public void cancel(Long taskId, Long versionId, DatasetType dataset) {
        }
    }

    private static class RecordingLlm implements LlmClient {
        private final FakeLlmClient delegate = new FakeLlmClient();
        final List<LlmContext> contexts = new ArrayList<>();

        @Override
        public LlmDecision decide(LlmContext context) {
            contexts.add(context);
            return delegate.decide(context);
        }
    }

    private AgentWorkflowServiceImpl wire(InMemoryAgentRepositories repos, RecordingLlm llm, FakeRouter router) {
        LlmStepExecutor executor = new LlmStepExecutor(llm, new LlmDecisionValidator(),
                repos.versionRepository, repos.stepRepository);
        return new AgentWorkflowServiceImpl(repos.taskRepository, repos.versionRepository,
                repos.evaluationRunRepository, executor, router, new BestVersionSelector(), 100L);
    }

    @Test
    void threeRoundLoopAcceptance() {
        InMemoryAgentRepositories repos = new InMemoryAgentRepositories();
        RecordingLlm llm = new RecordingLlm();
        AgentWorkflowServiceImpl workflow = wire(repos, llm, new FakeRouter(true, false));
        AgentTask task = newTask(3);
        repos.taskRepository.seed(task);

        workflow.runTask(task.getId());

        AgentTask done = repos.taskRepository.findById(task.getId());
        assertEquals("COMPLETED", done.getStatus());
        assertNotNull(done.getBestVersionId());
        assertNull(done.getActiveSlot());

        List<BotVersion> successVersions = repos.versionRepository.findByTask(task.getId()).stream()
                .filter(v -> "SUCCESS".equals(v.getCompileStatus()))
                .collect(Collectors.toList());
        assertTrue(successVersions.size() <= 3, "展示版本最多 3 个");
        assertEquals(3, successVersions.size());

        long accepted = successVersions.stream().filter(v -> v.getAccepted() != null && v.getAccepted() == 1).count();
        assertEquals(1, accepted, "恰好一个最佳版本被接受");

        BotVersion best = repos.versionRepository.findById(done.getBestVersionId());
        assertEquals(2, best.getIteration(), "V2 隐藏得分最高");

        for (LlmContext ctx : llm.contexts) {
            assertTrue(ctx.getPublicEvaluation() == null
                            || ctx.getPublicEvaluation().getDatasetType() == DatasetType.PUBLIC,
                    "LLM 上下文不得含隐藏集");
        }
    }

    @Test
    void compileSecondFailureFailsTask() {
        InMemoryAgentRepositories repos = new InMemoryAgentRepositories();
        RecordingLlm llm = new RecordingLlm();
        AgentWorkflowServiceImpl workflow = wire(repos, llm, new FakeRouter(false, false));
        AgentTask task = newTask(3);
        repos.taskRepository.seed(task);

        workflow.runTask(task.getId());

        AgentTask done = repos.taskRepository.findById(task.getId());
        assertEquals("FAILED", done.getStatus());
        assertEquals("COMPILE_FAILED", done.getErrorCode());
        assertNull(done.getActiveSlot());
    }

    @Test
    void publicEvaluationTimeoutFailsTask() {
        InMemoryAgentRepositories repos = new InMemoryAgentRepositories();
        RecordingLlm llm = new RecordingLlm();
        AgentWorkflowServiceImpl workflow = wire(repos, llm, new FakeRouter(true, true));
        AgentTask task = newTask(3);
        repos.taskRepository.seed(task);

        workflow.runTask(task.getId());

        AgentTask done = repos.taskRepository.findById(task.getId());
        assertEquals("FAILED", done.getStatus());
        assertTrue(AgentTaskStatus.valueOf(done.getStatus()).isTerminal());
    }
}
