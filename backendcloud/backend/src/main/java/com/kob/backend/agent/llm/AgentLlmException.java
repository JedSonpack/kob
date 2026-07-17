package com.kob.backend.agent.llm;

import com.kob.backend.agent.model.AgentErrorCode;

/**
 * LLM 调用异常，携带标准错误码（LLM_TIMEOUT / LLM_INVALID_RESPONSE）。
 */
public class AgentLlmException extends RuntimeException {
    private final AgentErrorCode code;

    public AgentLlmException(AgentErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AgentLlmException(AgentErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AgentErrorCode getCode() {
        return code;
    }
}
