package com.kob.backend.agent.service.impl;

/**
 * Agent 任务业务异常基类（HTTP 400）。
 */
public class AgentTaskException extends RuntimeException {
    public AgentTaskException(String message) {
        super(message);
    }
}
