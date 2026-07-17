package com.kob.backend.agent.dto;

import com.kob.backend.agent.model.AgentTask;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
public class AgentTaskListItemDto {
    private Long id;
    private String status;
    private String goal;
    private Integer currentIteration;
    private Integer maxIterations;
    private Long bestVersionId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date createdAt;

    public AgentTaskListItemDto(AgentTask task) {
        this.id = task.getId();
        this.status = task.getStatus();
        this.goal = task.getGoal();
        this.currentIteration = task.getCurrentIteration();
        this.maxIterations = task.getMaxIterations();
        this.bestVersionId = task.getBestVersionId();
        this.createdAt = task.getCreatedAt();
    }
}
