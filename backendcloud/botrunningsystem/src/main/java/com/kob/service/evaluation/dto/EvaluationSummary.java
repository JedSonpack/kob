package com.kob.service.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 评测聚合指标。
 *
 * <p>score = (wins + 0.5 * draws) / gameCount；winRate = wins / gameCount；
 * decisionP95Ms 使用向上取整索引的 P95。
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
