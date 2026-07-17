package com.kob.service.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单局评测结果（候选 Bot 视角）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationMatchResult {
    private String opponentKey;
    private long mapSeed;
    private String side;
    private String result;        // WIN / LOSS / DRAW
    private int rounds;
    private long decisionP95Ms;
    private int invalidMoveCount;
    private String failureReason;
    private String replay;        // aDir,bDir;... 或 REPLAY_LIMIT
}
