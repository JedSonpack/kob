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
 * Agent 任务。状态枚举以 name() 存入 {@code status} 列，{@code version} 为乐观锁版本号。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_task")
public class AgentTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer userId;
    private String goal;
    private String status;
    private Integer currentIteration;
    private Integer maxIterations;
    private Long bestVersionId;
    private Integer activeSlot;
    private Integer version;
    private String errorCode;
    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date updatedAt;
}
