package com.kob.backend.agent.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateAgentTaskRequest {
    private String goal;
    private Integer maxIterations;
}
