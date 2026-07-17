package com.kob.service.evaluation.sandbox;

/**
 * 沙箱执行标准错误码。
 */
public enum SandboxErrorCode {
    COMPILE_FAILED,
    STEP_TIMEOUT,
    SANDBOX_VIOLATION,
    OUTPUT_LIMIT,
    INVALID_MOVE,
    PROTOCOL_ERROR,
    CANCELLED
}
