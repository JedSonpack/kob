package com.kob.backend.agent.workflow;

import com.kob.backend.agent.llm.FakeLlmClient;
import com.kob.backend.agent.llm.LlmClient;
import com.kob.backend.agent.llm.LlmContext;
import com.kob.backend.agent.llm.LlmDecision;
import com.kob.backend.agent.llm.LlmDecisionValidator;
import com.kob.backend.agent.llm.LlmStepExecutor;
import com.kob.backend.agent.model.AgentTask;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorkflowServiceImplTest {

    private static AgentTask newTask(int maxIterations) {
        AgentTask task = new AgentTask();
        task.setUserId(1);
        task.setGoal("扩大可活动区域");
        task.setStatus("CREATED");
        task.setCurrentIteration(0);
        task.setMaxIterations(maxIterations);
        task.setActiveSlot(1);
        task.setVersion(0);
        task.setCreatedAt(new Date());
        task.setUpdatedAt(new Date());
        return task;
    }

    /** 始终编译成功；公开集 48 局 0 非法移动 P95=10；隐藏集按版本 iteration 给分（V2 最高）。 */
    private static class FakeRouter extends AgentToolRouter {
        FakeRouter() { super(null, null, null, null); }

        @Override
        public CompileToolResult compile(com.kob.backend.agent.model.AgentTask task, BotVersion version) {
            version.setCompileStatus("SUCCESS");
            return new CompileToolResult(true, null);
        }

        @Override
        public EvaluationAggregate evaluate(com.kob.backend.agent.model.AgentTask task, BotVersion version, DatasetType dataset) {
            if (dataset == DatasetType.PUBLIC) {
                return new EvaluationAggregate(dataset, 48, 0.5, 0.5, 50, 10, 0, new HashMap<String, Integer>());
            }
            double hidden = version.getIteration() == 2 ? 0.6 : (version.getIteration() == 3 ? 0.55 : 0.5);
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

    private AgentWorkflowServiceImpl wire(InMemoryAgentRepositories repos, RecordingLlm llm) {
        LlmStepExecutor executor = new LlmStepExecutor(llm, new LlmDecisionValidator(),
                repos.versionRepository, repos.stepRepository);
        return new AgentWorkflowServiceImpl(repos.taskRepository, repos.versionRepository,
                repos.evaluationRunRepository, executor, new FakeRouter(), new BestVersionSelector(), 100L);
    }

    @Test
    void threeRoundLoopCompletesAndSelectsV2() {
        InMemoryAgentRepositories repos = new InMemoryAgentRepositories();
        RecordingLlm llm = new RecordingLlm();
        AgentWorkflowServiceImpl workflow = wire(repos, llm);
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
        assertEquals(3, successVersions.size());

        BotVersion best = repos.versionRepository.findById(done.getBestVersionId());
        assertEquals(2, best.getIteration());
        assertEquals(1, best.getAccepted());

        // LLM 上下文从未含隐藏集
        for (LlmContext ctx : llm.contexts) {
            assertTrue(ctx.getPublicEvaluation() == null
                            || ctx.getPublicEvaluation().getDatasetType() == DatasetType.PUBLIC,
                    "LLM 上下文不得含隐藏集");
        }
    }

    @Test
    void earlyFinishWhenMaxIterationsReached() {
        InMemoryAgentRepositories repos = new InMemoryAgentRepositories();
        RecordingLlm llm = new RecordingLlm();
        AgentWorkflowServiceImpl workflow = wire(repos, llm);
        AgentTask task = newTask(1);
        repos.taskRepository.seed(task);

        workflow.runTask(task.getId());

        AgentTask done = repos.taskRepository.findById(task.getId());
        assertEquals("COMPLETED", done.getStatus());
        List<BotVersion> successVersions = repos.versionRepository.findByTask(task.getId()).stream()
                .filter(v -> "SUCCESS".equals(v.getCompileStatus()))
                .collect(Collectors.toList());
        assertEquals(1, successVersions.size());
        assertEquals(1, successVersions.get(0).getIteration());
    }
}
