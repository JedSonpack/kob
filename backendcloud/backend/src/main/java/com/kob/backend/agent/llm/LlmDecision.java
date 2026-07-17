package com.kob.backend.agent.llm;

import com.kob.backend.agent.model.AgentAction;

/**
 * LLM 结构化决策。动作由服务端按当前状态校验。
 */
public final class LlmDecision {
    private final AgentAction action;
    private final String strategySummary;
    private final String changeReason;
    private final String sourceCode;
    private final Integer promptTokens;
    private final Integer completionTokens;

    public LlmDecision(AgentAction action, String strategySummary, String changeReason,
                       String sourceCode, Integer promptTokens, Integer completionTokens) {
        this.action = action;
        this.strategySummary = strategySummary;
        this.changeReason = changeReason;
        this.sourceCode = sourceCode;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public AgentAction getAction() { return action; }
    public String getStrategySummary() { return strategySummary; }
    public String getChangeReason() { return changeReason; }
    public String getSourceCode() { return sourceCode; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
}
