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
 * Agent 工具调用 Trace：幂等键、阶段、脱敏摘要、状态、耗时与 Token。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_step")
public class AgentStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer sequence;
    private String phase;
    private String toolName;
    private String idempotencyKey;
    private String inputSummary;
    private String outputSummary;
    private String status;
    private Long durationMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private String errorCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date createdAt;
}
