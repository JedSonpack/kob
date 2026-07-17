package com.kob.backend.agent.llm;

import com.kob.backend.agent.model.AgentAction;
import com.kob.backend.agent.model.AgentTaskStatus;
import org.springframework.stereotype.Component;

/**
 * LLM 决策校验器：动作必须符合当前状态；非 FINISH 必须有 1..10000 字符源码；策略摘要必填。
 */
@Component
public class LlmDecisionValidator {

    private static final int MAX_SOURCE = 10000;

    public void validate(AgentTaskStatus status, LlmDecision decision) {
        if (decision == null || decision.getAction() == null) {
            throw new IllegalArgumentException("动作缺失");
        }
        if (!decision.getAction().isAllowedIn(status)) {
            throw new IllegalArgumentException("动作越权: " + decision.getAction() + " in " + status);
        }
        if (decision.getAction() != AgentAction.FINISH) {
            if (decision.getSourceCode() == null || decision.getSourceCode().isEmpty()) {
                throw new IllegalArgumentException("源码为空");
            }
            if (decision.getSourceCode().length() > MAX_SOURCE) {
                throw new IllegalArgumentException("源码超过 " + MAX_SOURCE + " 字符");
            }
        }
        if (decision.getStrategySummary() == null || decision.getStrategySummary().isEmpty()) {
            throw new IllegalArgumentException("策略摘要为空");
        }
    }
}
