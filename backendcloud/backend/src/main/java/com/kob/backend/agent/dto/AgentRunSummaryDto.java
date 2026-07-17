package com.kob.backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测录像摘要（前端 Replay 入口）。仅暴露对手、出生侧、胜负、回合、失败原因与数据集类型，
 * 不含地图种子与可重放移动序列，避免隐藏集种子经列表接口泄漏。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunSummaryDto {
    private Long id;
    private Long versionId;
    private String opponentKey;
    private String side;
    private String result;
    private Integer rounds;
    private Long decisionP95Ms;
    private String failureReason;
    private String datasetType;
}
