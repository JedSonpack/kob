package com.kob.backend.agent.tool;

import com.kob.backend.agent.model.DatasetType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 评测聚合指标（backend 侧），用于模型上下文（公开集）与最佳版本选择（隐藏集）。
 */
@Data
@AllArgsConstructor
public class EvaluationAggregate {
    private final DatasetType datasetType;
    private final int gameCount;
    private final double score;
    private final double winRate;
    private final double averageRounds;
    private final long decisionP95Ms;
    private final int invalidMoveCount;
    private final Map<String, Integer> failureCounts;
}
