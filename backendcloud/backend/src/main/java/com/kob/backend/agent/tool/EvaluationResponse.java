package com.kob.backend.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * botsys 评测接口响应 DTO（字段名与 botsys EvaluationResponse 一致）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationResponse {
    private String requestId;
    private boolean compileSucceeded;
    private String compileError;
    private EvaluationSummary summary;
    private List<EvaluationMatchResult> matches;
}
