package com.kob.backend.agent.llm;

import com.kob.backend.agent.model.BotVersion;

/**
 * LLM Step 执行结果：决策与（非 FINISH 时）持久化的版本。
 */
public final class LlmStepResult {
    private final LlmDecision decision;
    private final BotVersion version;

    public LlmStepResult(LlmDecision decision, BotVersion version) {
        this.decision = decision;
        this.version = version;
    }

    public LlmDecision getDecision() { return decision; }
    public BotVersion getVersion() { return version; }
}
