package com.kob.service.evaluation;

/**
 * 同一 requestId 正在执行时抛出，对应 HTTP 409。
 */
public class EvaluationConflictException extends RuntimeException {
    private final String requestId;

    public EvaluationConflictException(String requestId) {
        super("评测任务正在执行: " + requestId);
        this.requestId = requestId;
    }

    public String getRequestId() {
        return requestId;
    }
}
