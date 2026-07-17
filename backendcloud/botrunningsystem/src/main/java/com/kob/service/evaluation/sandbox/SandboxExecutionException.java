package com.kob.service.evaluation.sandbox;

/**
 * 沙箱执行异常，携带标准错误码与脱敏消息（最长 500 字符）。
 */
public class SandboxExecutionException extends RuntimeException {
    private static final int MAX_MESSAGE = 500;

    private final SandboxErrorCode code;

    public SandboxExecutionException(SandboxErrorCode code, String message) {
        super(truncate(message));
        this.code = code;
    }

    public SandboxExecutionException(SandboxErrorCode code, String message, Throwable cause) {
        super(truncate(message), cause);
        this.code = code;
    }

    public SandboxErrorCode getCode() { return code; }

    private static String truncate(String message) {
        if (message == null) return null;
        return message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE);
    }
}
