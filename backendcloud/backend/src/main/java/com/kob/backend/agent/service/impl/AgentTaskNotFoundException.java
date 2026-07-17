package com.kob.backend.agent.service.impl;

/**
 * Agent 任务/版本/录像不存在或不属于当前用户（HTTP 404）。
 */
public class AgentTaskNotFoundException extends AgentTaskException {
    public AgentTaskNotFoundException(String message) {
        super(message);
    }
}
