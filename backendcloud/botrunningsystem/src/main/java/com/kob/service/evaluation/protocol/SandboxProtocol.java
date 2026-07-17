package com.kob.service.evaluation.protocol;

/**
 * 持久沙箱子进程行协议常量。
 *
 * <p>父进程与 {@code PersistentSandboxMain} 通过制表符分隔的行通信：
 * <ul>
 *   <li>{@link #SOURCE} + '\t' + base64(源码)：下发候选源码。</li>
 *   <li>{@link #READY}：子进程编译完成并就绪。</li>
 *   <li>{@link #MOVE} + '\t' + base64(局面)：请求一步决策。</li>
 *   <li>{@link #RESULT} + '\t' + direction + '\t' + durationNanos：返回决策。</li>
 *   <li>{@link #ERROR} + '\t' + errorCode + '\t' + base64(脱敏消息)：子进程异常。</li>
 *   <li>{@link #STOP}：结束本版本，子进程退出。</li>
 * </ul>
 */
public final class SandboxProtocol {
    public static final String READY = "READY";
    public static final String SOURCE = "SOURCE";
    public static final String MOVE = "MOVE";
    public static final String RESULT = "RESULT";
    public static final String ERROR = "ERROR";
    public static final String STOP = "STOP";

    private SandboxProtocol() {}
}
