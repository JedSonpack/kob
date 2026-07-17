package com.kob.backend.agent.model;

/**
 * Agent 任务标准错误码（设计规格第 13 节）。
 */
public enum AgentErrorCode {
    LLM_TIMEOUT,
    LLM_INVALID_RESPONSE,
    COMPILE_FAILED,
    EVALUATION_TIMEOUT,
    SANDBOX_VIOLATION,
    INVALID_MOVE_RATE,
    TASK_CONFLICT,
    TASK_CANCELLED
}
