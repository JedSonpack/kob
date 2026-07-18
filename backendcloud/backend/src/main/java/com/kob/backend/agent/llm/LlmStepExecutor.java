package com.kob.backend.agent.llm;

import com.kob.backend.agent.model.AgentAction;
import com.kob.backend.agent.model.AgentErrorCode;
import com.kob.backend.agent.model.AgentStep;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.model.BotVersion;
import com.kob.backend.agent.repository.AgentStepRepository;
import com.kob.backend.agent.repository.BotVersionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM Step 执行器：幂等键 task:{taskId}:phase:{status}:iteration:{iteration}:llm；
 * 已有 SUCCESS Step 时恢复决策（不重复调用模型）；否则调用模型、校验、持久化版本与 Step。
 *
 * <p>GENERATE/REPAIR/IMPROVE 创建 bot_version 并标记 Step SUCCESS；FINISH 把动作与原因写入 output_summary。
 */
@Component
public class LlmStepExecutor {

    private static final int MAX_DECISION_ATTEMPTS = 2;

    private final LlmClient llmClient;
    private final LlmDecisionValidator validator;
    private final BotVersionRepository versionRepository;
    private final AgentStepRepository stepRepository;

    public LlmStepExecutor(LlmClient llmClient, LlmDecisionValidator validator,
                           BotVersionRepository versionRepository, AgentStepRepository stepRepository) {
        this.llmClient = llmClient;
        this.validator = validator;
        this.versionRepository = versionRepository;
        this.stepRepository = stepRepository;
    }

    public LlmStepResult execute(AgentTask task, LlmContext context, int versionIteration, int attempt, Long parentVersionId) {
        String key = "task:" + task.getId() + ":phase:" + context.getStatus().name()
                + ":iteration:" + context.getIteration() + ":llm";
        AgentStep existing = stepRepository.findByIdempotencyKey(key);
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            return recover(existing);
        }
        LlmDecision decision = decideValid(context);
        return persist(task, context, decision, versionIteration, attempt, parentVersionId, key);
    }

    private LlmDecision decideValid(LlmContext context) {
        LlmContext attemptContext = context;
        Integer promptTokens = null;
        Integer completionTokens = null;
        for (int attempt = 0; attempt < MAX_DECISION_ATTEMPTS; attempt++) {
            LlmDecision decision = llmClient.decide(attemptContext);
            promptTokens = addUsage(promptTokens, decision.getPromptTokens());
            completionTokens = addUsage(completionTokens, decision.getCompletionTokens());
            try {
                validator.validate(context.getStatus(), decision);
                return withUsage(decision, promptTokens, completionTokens);
            } catch (IllegalArgumentException validationError) {
                if (attempt == MAX_DECISION_ATTEMPTS - 1) {
                    throw new AgentLlmException(AgentErrorCode.LLM_INVALID_RESPONSE,
                            "LLM 决策校验重试耗尽: " + validationError.getMessage());
                }
                attemptContext = withValidationFeedback(attemptContext, validationError.getMessage());
            }
        }
        throw new IllegalStateException("LLM 决策校验重试耗尽");
    }

    private LlmContext withValidationFeedback(LlmContext context, String error) {
        List<String> failures = new ArrayList<>();
        if (context.getFailureSummaries() != null) {
            failures.addAll(context.getFailureSummaries());
        }
        failures.add("上次决策校验失败：" + error + "。请修正后重新调用 submit_decision。");
        return new LlmContext(context.getTaskId(), context.getStatus(), context.getGoal(),
                context.getIteration(), context.getMaxIterations(), context.getCurrentSourceCode(),
                context.getCompileError(), context.getPublicEvaluation(), failures,
                context.getPreviousChangeSummary());
    }

    private Integer addUsage(Integer total, Integer usage) {
        if (usage == null) {
            return total;
        }
        return (total == null ? 0 : total) + usage;
    }

    private LlmDecision withUsage(LlmDecision decision, Integer promptTokens, Integer completionTokens) {
        return new LlmDecision(decision.getAction(), decision.getStrategySummary(),
                decision.getChangeReason(), decision.getSourceCode(), promptTokens, completionTokens);
    }

    private LlmStepResult persist(AgentTask task, LlmContext context, LlmDecision decision,
                                  int versionIteration, int attempt, Long parentVersionId, String key) {
        AgentStep step = stepRepository.insertRunning(task.getId(), context.getStatus().name(),
                "llm", key, "iteration:" + versionIteration);
        if (decision.getAction() != AgentAction.FINISH) {
            BotVersion version = new BotVersion();
            version.setTaskId(task.getId());
            version.setIteration(versionIteration);
            version.setAttempt(attempt);
            version.setParentVersionId(parentVersionId);
            version.setSourceCode(decision.getSourceCode());
            version.setStrategySummary(decision.getStrategySummary());
            version.setChangeReason(decision.getChangeReason());
            version.setCompileStatus("PENDING");
            version.setAccepted(0);
            versionRepository.save(version);
            stepRepository.markSuccess(step.getId(), 0L,
                    "version:" + version.getId() + ":" + decision.getAction().name(),
                    decision.getPromptTokens(), decision.getCompletionTokens());
            return new LlmStepResult(decision, version);
        }
        stepRepository.markSuccess(step.getId(), 0L,
                "FINISH:" + (decision.getChangeReason() == null ? "" : decision.getChangeReason()),
                decision.getPromptTokens(), decision.getCompletionTokens());
        return new LlmStepResult(decision, null);
    }

    private LlmStepResult recover(AgentStep step) {
        String output = step.getOutputSummary();
        if (output != null && output.startsWith("FINISH:")) {
            LlmDecision decision = new LlmDecision(AgentAction.FINISH, "已恢复",
                    output.substring("FINISH:".length()), null,
                    step.getPromptTokens(), step.getCompletionTokens());
            return new LlmStepResult(decision, null);
        }
        // 格式 version:{versionId}:{ACTION}
        if (output != null && output.startsWith("version:")) {
            String[] parts = output.split(":", 3);
            if (parts.length == 3) {
                try {
                    Long versionId = Long.parseLong(parts[1]);
                    AgentAction action = AgentAction.valueOf(parts[2]);
                    BotVersion version = versionRepository.findById(versionId);
                    if (version != null) {
                        LlmDecision decision = new LlmDecision(action, version.getStrategySummary(),
                                version.getChangeReason(), version.getSourceCode(),
                                step.getPromptTokens(), step.getCompletionTokens());
                        return new LlmStepResult(decision, version);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        throw new IllegalStateException("无法从 Step 恢复 LLM 决策: " + output);
    }
}
