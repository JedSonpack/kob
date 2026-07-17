package com.kob.backend.agent.dto;

import com.kob.backend.agent.model.AgentStep;
import com.kob.backend.agent.model.AgentTask;
import com.kob.backend.agent.tool.EvaluationAggregate;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
public class AgentTaskDetailDto {
    private Long id;
    private Integer userId;
    private String status;
    private String goal;
    private Integer currentIteration;
    private Integer maxIterations;
    private Long bestVersionId;
    private String errorCode;
    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date createdAt;

    private List<AgentVersionDetailDto> versions;
    private List<AgentStep> steps;
    private EvaluationAggregate publicEvaluation;
    private EvaluationAggregate hiddenEvaluation;
    // 代表性录像摘要，仅在任务终态填充，供前端 Replay 面板选择 WIN/LOSS 各一条。
    private List<AgentRunSummaryDto> representativeRuns;

    public AgentTaskDetailDto(AgentTask task) {
        this.id = task.getId();
        this.userId = task.getUserId();
        this.status = task.getStatus();
        this.goal = task.getGoal();
        this.currentIteration = task.getCurrentIteration();
        this.maxIterations = task.getMaxIterations();
        this.bestVersionId = task.getBestVersionId();
        this.errorCode = task.getErrorCode();
        this.errorMessage = task.getErrorMessage();
        this.createdAt = task.getCreatedAt();
    }
}
