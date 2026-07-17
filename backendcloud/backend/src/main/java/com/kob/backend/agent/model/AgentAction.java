package com.kob.backend.agent.model;

/**
 * LLM 允许返回的结构化动作。服务端按当前状态校验，不直接信任模型。
 */
public enum AgentAction {
    GENERATE_CODE,
    REPAIR_CODE,
    IMPROVE_CODE,
    FINISH;

    public boolean isAllowedIn(AgentTaskStatus status) {
        switch (this) {
            case GENERATE_CODE:
                return status == AgentTaskStatus.GENERATING;
            case REPAIR_CODE:
                return status == AgentTaskStatus.REPAIRING;
            case IMPROVE_CODE:
            case FINISH:
                return status == AgentTaskStatus.ANALYZING;
            default:
                return false;
        }
    }
}
