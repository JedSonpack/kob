package com.kob.service.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评测请求：requestId（幂等键）、候选源码、模式。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationRequest {
    private String requestId;
    private String sourceCode;
    private EvaluationMode mode;
}
