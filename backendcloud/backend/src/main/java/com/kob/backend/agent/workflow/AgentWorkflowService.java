package com.kob.backend.agent.workflow;

/**
 * Agent 工作流服务：驱动状态机、恢复未完成任务、取消任务。
 */
public interface AgentWorkflowService {
    void runTask(Long taskId);

    void resumeIncompleteTasks();

    void cancelTask(Long taskId, Integer userId);
}
