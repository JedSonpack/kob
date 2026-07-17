package com.kob.backend.agent.llm;

/**
 * LLM 客户端抽象：根据上下文返回结构化决策。服务端状态机决定下一阶段，不信任模型自定阶段。
 */
public interface LlmClient {
    LlmDecision decide(LlmContext context);
}
