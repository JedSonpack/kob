package com.kob.backend.agent.controller;

import com.kob.backend.agent.service.AgentTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AgentEvaluationController {

    @Autowired
    private AgentTaskService agentTaskService;

    @GetMapping("/api/agent/evaluations/{runId}/replay/")
    public Map<String, Object> getReplay(@PathVariable Long runId) {
        return agentTaskService.getReplay(runId);
    }
}
