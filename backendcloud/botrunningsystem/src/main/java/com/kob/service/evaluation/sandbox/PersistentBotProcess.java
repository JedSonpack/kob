package com.kob.service.evaluation.sandbox;

import java.nio.file.Path;

/**
 * 持久化候选 Bot 子进程：每个版本只编译一次，连续响应多个局面。
 *
 * <p>子进程只接收当前局面编码，不接收种子、基准策略、数据集类型或最终胜负。
 * 超时、越权或异常时调用 {@link #cancel()} 并由 {@link #close()} 递归清理临时目录。
 */
public interface PersistentBotProcess extends AutoCloseable {

    /** 编译候选源码并就绪。编译失败抛 {@link SandboxExecutionException}（COMPILE_FAILED）。 */
    void start(String sourceCode);

    /** 请求一步决策，返回方向与耗时；越权/超时/协议错误抛 {@link SandboxExecutionException}。 */
    BotMove decide(String encodedSnapshot);

    /** 子进程是否存活。 */
    boolean isAlive();

    /** 本版本的独立临时工作目录。 */
    Path getWorkDir();

    /** 主动取消：终止子进程，标记关闭。 */
    void cancel();

    /** 幂等关闭：终止子进程并递归删除临时目录，重复调用不抛异常。 */
    @Override
    void close();
}
