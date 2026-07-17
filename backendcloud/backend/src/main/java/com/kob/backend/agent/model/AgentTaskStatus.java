package com.kob.backend.agent.model;

/**
 * Agent 任务状态机。
 *
 * <pre>CREATED -> GENERATING -> COMPILING
 * COMPILING -- 首次失败 --> REPAIRING -> COMPILING
 * COMPILING -- 成功 --> EVALUATING -> ANALYZING
 * ANALYZING -- 继续迭代 --> IMPROVING -> COMPILING
 * ANALYZING -- 结束迭代 --> VALIDATING -> COMPLETED</pre>
 * 任一执行中状态可进入 FAILED / CANCELLED（终态）。
 */
public enum AgentTaskStatus {
    CREATED,
    GENERATING,
    COMPILING,
    REPAIRING,
    EVALUATING,
    ANALYZING,
    IMPROVING,
    VALIDATING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
