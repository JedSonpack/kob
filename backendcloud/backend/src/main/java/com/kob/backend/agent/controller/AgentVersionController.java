package com.kob.backend.agent.controller;

import com.kob.backend.agent.dto.AgentVersionDetailDto;
import com.kob.backend.agent.dto.SaveAgentVersionRequest;
import com.kob.backend.agent.service.AgentTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentVersionController {

    @Autowired
    private AgentTaskService agentTaskService;

    @GetMapping("/api/agent/versions/{versionId}/")
    public AgentVersionDetailDto getVersionDetail(@PathVariable Long versionId) {
        return agentTaskService.getVersionDetail(versionId);
    }

    @PostMapping("/api/agent/versions/{versionId}/save-bot/")
    public java.util.Map<String, String> saveVersion(@PathVariable Long versionId,
                                                     @RequestBody SaveAgentVersionRequest request) {
        return agentTaskService.saveVersion(versionId, request);
    }
}
