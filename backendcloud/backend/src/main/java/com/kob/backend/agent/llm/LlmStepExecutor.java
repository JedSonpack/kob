package com.kob.backend.agent.llm;

import com.kob.backend.agent.model.AgentAction;
import com.kob.backend.agent.model.AgentStep;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.model.BotVersion;
import com.kob.backend.agent.repository.AgentStepRepository;
import com.kob.backend.agent.repository.BotVersionRepository;
import org.springframework.stereotype.Component;

/**
 * LLM Step 执行器：幂等键 task:{taskId}:phase:{status}:iteration:{iteration}:llm；
 * 已有 SUCCESS Step 时恢复决策（不重复调用模型）；否则调用模型、校验、持久化版本与 Step。
 *
 * <p>GENERATE/REPAIR/IMPROVE 创建 bot_version 并标记 Step SUCCESS；FINISH 把动作与原因写入 output_summary。
 */
@Component
public class LlmStepExecutor {

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

    public LlmStepResult execute(AgentTask task, LlmContext context, int iteration, int attempt, Long parentVersionId) {
        String key = "task:" + task.getId() + ":phase:" + context.getStatus().name()
                + ":iteration:" + iteration + ":llm";
        AgentStep existing = stepRepository.findByIdempotencyKey(key);
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            return recover(existing);
        }
        LlmDecision decision = llmClient.decide(context);
        validator.validate(context.getStatus(), decision);
        return persist(task, context, decision, iteration, attempt, parentVersionId, key);
    }

    private LlmStepResult persist(AgentTask task, LlmContext context, LlmDecision decision,
                                  int iteration, int attempt, Long parentVersionId, String key) {
        AgentStep step = stepRepository.insertRunning(task.getId(), context.getStatus().name(),
                "llm", key, "iteration:" + iteration);
        if (decision.getAction() != AgentAction.FINISH) {
            BotVersion version = new BotVersion();
            version.setTaskId(task.getId());
            version.setIteration(iteration);
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
