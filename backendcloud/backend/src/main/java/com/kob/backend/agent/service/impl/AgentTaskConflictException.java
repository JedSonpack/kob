package com.kob.backend.agent.service.impl;

/**
 * Agent 任务冲突（HTTP 409）：已有运行任务、保存条件不满足、隐藏集运行中查看等。
 */
public class AgentTaskConflictException extends AgentTaskException {
    public AgentTaskConflictException(String message) {
        super(message);
    }
}
