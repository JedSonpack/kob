package com.kob.backend.agent.dto;

import com.kob.backend.agent.model.BotVersion;
import com.kob.backend.agent.tool.EvaluationAggregate;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
public class AgentVersionDetailDto {
    private Long id;
    private Integer iteration;
    private Integer attempt;
    private String strategySummary;
    private String changeReason;
    private String compileStatus;
    private boolean accepted;
    private Integer publicGameCount;
    private Double publicScore;
    private Long publicP95Ms;
    private Double publicWinRate;
    private Double publicAverageRounds;
    private Integer publicInvalidMoveCount;
    // 源码与父版本只在单版本详情接口返回，不进入每秒轮询的任务详情，避免响应膨胀。
    private String sourceCode;
    private Long parentVersionId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date createdAt;

    public AgentVersionDetailDto(BotVersion version, EvaluationAggregate publicEval) {
        this.id = version.getId();
        this.iteration = version.getIteration();
        this.attempt = version.getAttempt();
        this.strategySummary = version.getStrategySummary();
        this.changeReason = version.getChangeReason();
        this.compileStatus = version.getCompileStatus();
        this.accepted = version.getAccepted() != null && version.getAccepted() == 1;
        this.createdAt = version.getCreatedAt();
        if (publicEval != null) {
            this.publicGameCount = publicEval.getGameCount();
            this.publicScore = publicEval.getScore();
            this.publicP95Ms = publicEval.getDecisionP95Ms();
            this.publicWinRate = publicEval.getWinRate();
            this.publicAverageRounds = publicEval.getAverageRounds();
            this.publicInvalidMoveCount = publicEval.getInvalidMoveCount();
        }
    }
}
