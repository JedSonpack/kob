package com.kob.service.evaluation;

import com.kob.service.evaluation.dto.EvaluationRequest;
import com.kob.service.evaluation.dto.EvaluationResponse;

/**
 * 评测服务：幂等批量评测与主动取消。
 */
public interface EvaluationService {

    EvaluationResponse evaluate(EvaluationRequest request);

    boolean cancel(String requestId);
}
