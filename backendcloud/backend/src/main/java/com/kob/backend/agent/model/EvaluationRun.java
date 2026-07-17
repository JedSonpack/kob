package com.kob.backend.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 单局评测记录（候选 Bot 视角）。隐藏集记录仅在任务终态后对外暴露聚合。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("evaluation_run")
public class EvaluationRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long versionId;
    private String datasetType;
    private String opponentKey;
    private Long mapSeed;
    private String side;
    private String result;
    private Integer rounds;
    private Long decisionP95Ms;
    private Integer invalidMoveCount;
    private String failureReason;
    private String replay;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date createdAt;
}
