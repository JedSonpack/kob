package com.kob.backend.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * botsys 单局评测结果 DTO（字段名与 botsys EvaluationMatchResult 一致）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationMatchResult {
    private String opponentKey;
    private long mapSeed;
    private String side;
    private String result;
    private int rounds;
    private long decisionP95Ms;
    private int invalidMoveCount;
    private String failureReason;
    private String replay;
}
