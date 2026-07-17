package com.kob.backend.agent.controller;

import com.kob.backend.agent.dto.AgentTaskDetailDto;
import com.kob.backend.agent.dto.AgentTaskListItemDto;
import com.kob.backend.agent.dto.CreateAgentTaskRequest;
import com.kob.backend.agent.service.AgentTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AgentTaskController {

    @Autowired
    private AgentTaskService agentTaskService;

    @PostMapping("/api/agent/tasks/")
    public Map<String, Object> createTask(@RequestBody CreateAgentTaskRequest request) {
        Long taskId = agentTaskService.createTask(request);
        Map<String, Object> result = new HashMap<>();
        result.put("error_message", "success");
        result.put("task_id", taskId);
        return result;
    }

    @GetMapping("/api/agent/tasks/")
    public List<AgentTaskListItemDto> listTasks() {
        return agentTaskService.listTasks();
    }

    @GetMapping("/api/agent/tasks/{taskId}/")
    public AgentTaskDetailDto getTaskDetail(@PathVariable Long taskId) {
        return agentTaskService.getTaskDetail(taskId);
    }

    @PostMapping("/api/agent/tasks/{taskId}/cancel/")
    public Map<String, Object> cancelTask(@PathVariable Long taskId) {
        agentTaskService.cancelTask(taskId);
        Map<String, Object> result = new HashMap<>();
        result.put("error_message", "success");
        return result;
    }
}
