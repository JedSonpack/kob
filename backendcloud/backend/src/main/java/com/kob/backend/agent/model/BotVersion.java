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
 * Bot 版本：迭代轮次、编译尝试、父子关系、源码与编译结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("bot_version")
public class BotVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer iteration;
    private Integer attempt;
    private Long parentVersionId;
    private String sourceCode;
    private String strategySummary;
    private String changeReason;
    private String compileStatus;
    private String compileError;
    private Integer accepted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date createdAt;
}
