package com.kob.service.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评测响应：编译结果、聚合指标与每局结果。
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
