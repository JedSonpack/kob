package com.kob.backend.agent.service;

import com.kob.backend.agent.dto.AgentTaskDetailDto;
import com.kob.backend.agent.dto.AgentTaskListItemDto;
import com.kob.backend.agent.dto.AgentVersionDetailDto;
import com.kob.backend.agent.dto.CreateAgentTaskRequest;
import com.kob.backend.agent.dto.SaveAgentVersionRequest;

import java.util.List;
import java.util.Map;

/**
 * Agent 任务用户服务：创建、查询、取消、版本详情、回放与保存为正式 Bot。
 */
public interface AgentTaskService {

    Long createTask(CreateAgentTaskRequest request);

    List<AgentTaskListItemDto> listTasks();

    AgentTaskDetailDto getTaskDetail(Long taskId);

    void cancelTask(Long taskId);

    AgentVersionDetailDto getVersionDetail(Long versionId);

    Map<String, Object> getReplay(Long runId);

    Map<String, String> saveVersion(Long versionId, SaveAgentVersionRequest request);
}
