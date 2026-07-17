package com.kob.backend.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * botsys 评测聚合指标 DTO（字段名与 botsys EvaluationSummary 一致）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationSummary {
    private int gameCount;
    private double score;
    private double winRate;
    private double averageRounds;
    private long decisionP95Ms;
    private int invalidMoveCount;
    private Map<String, Integer> failureCounts;
}
