# KOB Agent Lab 阶段 2：持久沙箱与批量评测实施计划

> **面向智能体工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐项实施此计划。步骤使用复选框（`- [ ]`）语法跟踪。

**目标：** 在 `botrunningsystem` 中实现可信批量评测协调器，使候选 Bot 在独立持久 JVM 中只编译一次并连续响应多个 `GameSnapshot`，同时提供公开集、隐藏集、超时、取消和资源清理能力。

**架构：** `EvaluationCoordinator` 在可信主 JVM 中持有 `game-core`、地图种子、基准策略、胜负计算和指标聚合。`PersistentBotProcess` 通过 Base64 行协议与 `PersistentSandboxMain` 通信；候选进程只接收当前局面编码并返回方向和决策耗时。

**技术栈：** Java 8、Spring Boot 2.4.5、jOOR Java 8、`ProcessBuilder`、`SecurityManager`、JUnit 5

## 全局约束

- 不修改或删除现有 `/bot/add/` 在线单步执行链路。
- 候选源码只在独立 JVM 中编译和执行。
- `game-core`、基准策略、种子、胜负和指标聚合只在可信协调器 JVM 中运行。
- 持久子进程每个候选版本只编译一次。
- 子进程只接收当前局面，不接收种子、基准策略名称、数据集类型或最终胜负。
- 单步决策默认超时 200 ms；整批公开集默认超时 120 秒；隐藏集默认超时 60 秒。
- 候选输出上限默认 8192 字节，Replay 上限默认 1 MB。
- 超时、取消和异常必须调用 `destroyForcibly()` 并递归清理临时目录。
- 公开种子固定为 8 个，隐藏种子固定为 4 个；隐藏种子仅存在于 `botrunningsystem` 配置。
- 不新增 JSON 库；HTTP DTO 使用 Spring/Jackson，子进程协议使用 JDK Base64 和制表符分隔。
- Java 8 `SecurityManager` 只作为本地演示边界，文档不得宣传为生产级容器隔离。
- 执行本计划 Maven 命令前先运行：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home
export PATH="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin:$PATH"
```

---

## 文件结构

### 创建

- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/protocol/SandboxProtocol.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/protocol/SnapshotInputCodec.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/PersistentSandboxMain.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/PersistentBotProcess.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/ProcessPersistentBotProcess.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/PersistentBotProcessFactory.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/BotMove.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/SandboxErrorCode.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/SandboxExecutionException.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/dto/EvaluationMode.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/dto/EvaluationRequest.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/dto/EvaluationResponse.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/dto/EvaluationSummary.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/dto/EvaluationMatchResult.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/EvaluationCoordinator.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/EvaluationJobRegistry.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/EvaluationService.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/impl/EvaluationServiceImpl.java`
- `backendcloud/botrunningsystem/src/main/java/com/kob/controller/EvaluationController.java`
- 对应 `backendcloud/botrunningsystem/src/test/java/com/kob/service/evaluation/**` 测试。

### 修改

- `backendcloud/botrunningsystem/pom.xml`：依赖 `game-core`。
- `backendcloud/botrunningsystem/src/main/java/com/kob/config/SecurityConfig.java`：本地放行评测接口。
- `backendcloud/botrunningsystem/src/main/resources/application.properties`：评测限制与种子。

---

### 任务 1：定义快照编码和子进程行协议

**文件：**
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/protocol/SandboxProtocol.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/protocol/SnapshotInputCodec.java`
- 测试：`backendcloud/botrunningsystem/src/test/java/com/kob/service/evaluation/protocol/SnapshotInputCodecTest.java`
- 修改：`backendcloud/botrunningsystem/pom.xml`

**接口：**
- 消费：`com.kob.game.core.GameSnapshot`。
- 产出：`String SnapshotInputCodec.encode(GameSnapshot)` 和固定协议常量。

- [ ] **步骤 1：编写失败测试**

```java
package com.kob.service.evaluation.protocol;

import com.kob.game.core.GameSnapshot;
import com.kob.game.core.Position;
import com.kob.game.core.SnakeState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SnapshotInputCodecTest {
    @Test
    void encodesOnlyCurrentBoardAndTwoSnakes() {
        int[][] map = {{1, 1, 1}, {1, 0, 1}, {1, 1, 1}};
        GameSnapshot snapshot = new GameSnapshot(
                2,
                map,
                new SnakeState(new Position(1, 1), Arrays.asList(0, 1)),
                new SnakeState(new Position(1, 1), Collections.singletonList(2))
        );

        String encoded = new SnapshotInputCodec().encode(snapshot);

        assertEquals("111101111#1#1#(01)#1#1#(2)", encoded);
        assertFalse(encoded.contains("PUBLIC"));
        assertFalse(encoded.contains("HIDDEN"));
        assertFalse(encoded.contains("seed"));
    }
}
```

- [ ] **步骤 2：确认测试失败**

运行：

```bash
cd backendcloud
mvn -pl botrunningsystem -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=SnapshotInputCodecTest test
```

预期：缺少编码器。

- [ ] **步骤 3：实现编码器和协议常量**

`SnapshotInputCodec.encode` 必须生成与现有 Bot 模板兼容的格式：

```text
地图#我的出生行#我的出生列#(我的方向历史)#对手出生行#对手出生列#(对手方向历史)
```

`SandboxProtocol`：

```java
package com.kob.service.evaluation.protocol;

public final class SandboxProtocol {
    public static final String READY = "READY";
    public static final String SOURCE = "SOURCE";
    public static final String MOVE = "MOVE";
    public static final String RESULT = "RESULT";
    public static final String ERROR = "ERROR";
    public static final String STOP = "STOP";

    private SandboxProtocol() {}
}
```

源码和快照字段都使用 `Base64.getEncoder().encodeToString(bytes)`，字符集固定为 `StandardCharsets.UTF_8`。

- [ ] **步骤 4：运行协议测试**

运行：

```bash
cd backendcloud
mvn -pl botrunningsystem -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=SnapshotInputCodecTest test
```

预期：通过。

- [ ] **步骤 5：提交**

```bash
git add backendcloud/botrunningsystem/pom.xml \
  backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/protocol \
  backendcloud/botrunningsystem/src/test/java/com/kob/service/evaluation/protocol
git commit -m "feat(评测协议): 固定候选 Bot 可见的最小局面"
```

### 任务 2：实现只编译一次的持久沙箱子进程

**文件：**
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/PersistentSandboxMain.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/PersistentBotProcess.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/ProcessPersistentBotProcess.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/PersistentBotProcessFactory.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/BotMove.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/SandboxErrorCode.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/SandboxExecutionException.java`
- 测试：`backendcloud/botrunningsystem/src/test/java/com/kob/service/evaluation/sandbox/PersistentBotProcessTest.java`

**接口：**
- 产出：

```java
public interface PersistentBotProcess extends AutoCloseable {
    void start(String sourceCode);
    BotMove decide(String encodedSnapshot);
    boolean isAlive();
    Path getWorkDir();
    void cancel();
}
```

`BotMove` 包含 `int direction` 与 `long durationNanos`。

`SandboxErrorCode` 固定为：

```java
COMPILE_FAILED, STEP_TIMEOUT, SANDBOX_VIOLATION,
OUTPUT_LIMIT, INVALID_MOVE, PROTOCOL_ERROR, CANCELLED
```

`SandboxExecutionException` 包含 `SandboxErrorCode code` 和最多 500 个字符的脱敏消息。

- [ ] **步骤 1：编写 JDK 8 失败测试**

测试源码：

```java
private static final String COUNTING_BOT =
        "package com.kob.test;\n" +
        "public class Bot {\n" +
        "  private int calls = 0;\n" +
        "  public Integer nextMove(String input) { return calls++ % 4; }\n" +
        "}";
```

测试断言：

```java
process.start(COUNTING_BOT);
assertEquals(0, process.decide("first").getDirection());
assertEquals(1, process.decide("second").getDirection());
assertEquals(2, process.decide("third").getDirection());
assertTrue(process.isAlive());
Path workDir = process.getWorkDir();
process.close();
assertFalse(Files.exists(workDir));
```

计数连续递增证明同一对象和同一 JVM 被复用，而不是每步重新编译。

- [ ] **步骤 2：确认测试失败**

运行：

```bash
cd backendcloud
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home \
  mvn -pl botrunningsystem -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=PersistentBotProcessTest test
```

预期：缺少持久进程类型。

- [ ] **步骤 3：实现子进程入口**

`PersistentSandboxMain` 固定流程：

```text
1. 保存原始 System.out 为协议输出流。
2. 从 stdin 读取 `SOURCE\t<base64>`。
3. 使用 Reflect.compile("com.kob.test.Bot", sourceCode) 编译。
4. 安装与现有 SandboxMain 等价的 SecurityManager。
5. 创建 Bot 实例并验证存在 `Integer nextMove(String input)`。
6. 输出 READY。
7. 循环读取 MOVE、STOP。
8. MOVE 时把候选 System.out 临时替换为有上限的内存流，调用 nextMove。
9. 使用协议输出 `RESULT\t<direction>\t<durationNanos>`。
10. 编译或执行异常输出 `ERROR\t<errorCode>\t<base64脱敏消息>`。
```

子进程不得打印栈到协议 stdout；完整栈只写 stderr。

- [ ] **步骤 4：实现父进程**

`PersistentBotProcessFactory` 从配置读取：

```properties
kob.bot.evaluation.java-home=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home
kob.bot.evaluation.step-timeout-ms=200
kob.bot.evaluation.output-limit-bytes=8192
```

`ProcessPersistentBotProcess` 必须：

- 为每个版本创建 `kob-evaluation-*` 临时目录。
- 启动 `PersistentSandboxMain`。
- 清空继承环境，只写入运行 JDK 所需的 `JAVA_HOME`、`PATH`、`TMPDIR` 和 `LANG`，不得把数据库、JWT 或 LLM 环境变量传入候选进程。
- 使用一个单线程 `ExecutorService` 执行阻塞 `readLine()`，并通过 `Future.get(stepTimeoutMs, MILLISECONDS)` 限时。
- 校验方向在 `0..3`。
- 超时或协议错误时立即 `cancel()`。
- `close()` 幂等，重复调用不抛异常。
- 递归删除临时目录。

- [ ] **步骤 5：运行持久进程测试**

运行：

```bash
cd backendcloud
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home \
  mvn -pl botrunningsystem -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=PersistentBotProcessTest test
```

预期：多次移动、编译错误、关闭清理测试通过。

- [ ] **步骤 6：提交**

```bash
git add backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox \
  backendcloud/botrunningsystem/src/test/java/com/kob/service/evaluation/sandbox
git commit -m "feat(沙箱): 复用隔离 JVM 完成连续 Bot 决策"
```

### 任务 3：锁定沙箱超时、越权和输出上限

**文件：**
- 修改：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/PersistentSandboxMain.java`
- 修改：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox/PersistentBotProcess.java`
- 测试：`backendcloud/botrunningsystem/src/test/java/com/kob/service/evaluation/sandbox/PersistentSandboxSecurityTest.java`

**接口：**
- 产出：标准错误码 `COMPILE_FAILED`、`STEP_TIMEOUT`、`SANDBOX_VIOLATION`、`OUTPUT_LIMIT`、`INVALID_MOVE`。

- [ ] **步骤 1：编写失败测试**

分别提交以下 Bot：

```java
public Integer nextMove(String input) { while (true) {} }
```

```java
public Integer nextMove(String input) throws Exception {
    new java.net.Socket("127.0.0.1", 3000);
    return 0;
}
```

```java
public Integer nextMove(String input) throws Exception {
    new java.io.FileOutputStream("owned.txt").write(1);
    return 0;
}
```

```java
public Integer nextMove(String input) throws Exception {
    Runtime.getRuntime().exec("echo owned");
    return 0;
}
```

```java
public Integer nextMove(String input) {
    for (int i = 0; i < 100000; i++) System.out.print("x");
    return 0;
}
```

以及读取可信资源和环境变量的 Bot：

```java
public Integer nextMove(String input) throws Exception {
    java.io.InputStream stream =
        getClass().getClassLoader().getResourceAsStream("application.properties");
    if (stream != null) return 1;
    return System.getenv("KOB_BOT_HIDDEN_SEEDS") == null ? 0 : 2;
}
```

每个测试必须断言标准错误码、子进程已死亡、临时目录已删除。

- [ ] **步骤 2：确认至少一个安全测试失败**

运行：

```bash
cd backendcloud
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home \
  mvn -pl botrunningsystem -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=PersistentSandboxSecurityTest test
```

预期：未实现限制的用例失败。

- [ ] **步骤 3：实现限制**

- `SecurityManager` 拒绝 `SocketPermission`。
- `FilePermission` 拒绝 `write`、`delete` 和 `execute`；安装后只允许读取 `java.home` 下的 JDK 文件，不允许读取应用 classpath、`application.properties` 或临时目录中的其他文件。
- `RuntimePermission("exec")` 拒绝。
- 拒绝 `RuntimePermission("getenv.*")`，并由父进程清空敏感环境作为第二道边界。
- 捕获候选 stdout 的流在超过配置字节数时抛 `OutputLimitExceededException`。
- 父进程发现超时、EOF 或协议错误后调用 `destroyForcibly()` 并等待最多 1 秒。
- 只把异常类名和前 500 个字符的消息返回给协调器。

- [ ] **步骤 4：运行安全测试**

运行同上。

预期：所有恶意 Bot 被拒绝，且无残留进程和目录。

- [ ] **步骤 5：提交**

```bash
git add backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox \
  backendcloud/botrunningsystem/src/test/java/com/kob/service/evaluation/sandbox
git commit -m "fix(沙箱): 为批量评测建立资源与权限上限"
```

### 任务 4：实现批量评测 DTO 与指标聚合

**文件：**
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/dto/EvaluationMode.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/dto/EvaluationRequest.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/dto/EvaluationResponse.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/dto/EvaluationSummary.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/dto/EvaluationMatchResult.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/EvaluationCoordinator.java`
- 测试：`backendcloud/botrunningsystem/src/test/java/com/kob/service/evaluation/EvaluationCoordinatorTest.java`

**接口：**
- 消费：`GameEngine`、3 个基准策略、`PersistentBotProcessFactory`。
- 产出：`EvaluationResponse EvaluationCoordinator.evaluate(EvaluationRequest)`。

- [ ] **步骤 1：编写失败测试**

使用 Fake `PersistentBotProcess` 固定返回安全方向，公开集断言：

```java
EvaluationResponse response = coordinator.evaluate(
        new EvaluationRequest("req-1", SOURCE, EvaluationMode.PUBLIC)
);

assertTrue(response.isCompileSucceeded());
assertEquals(48, response.getSummary().getGameCount());
assertEquals(48, response.getMatches().size());
assertEquals(0, response.getSummary().getInvalidMoveCount());
assertEquals(1, fakeProcessFactory.getCreatedCount());
```

隐藏集断言 `24` 局。另写聚合测试断言平局按 `0.5` 得分，P95 使用向上取整索引：

```java
int index = (int) Math.ceil(values.size() * 0.95) - 1;
```

- [ ] **步骤 2：确认测试失败**

运行：

```bash
cd backendcloud
mvn -pl botrunningsystem -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=EvaluationCoordinatorTest test
```

预期：缺少协调器和 DTO。

- [ ] **步骤 3：实现 DTO**

`EvaluationRequest` 字段固定为：

```java
String requestId
String sourceCode
EvaluationMode mode
```

`EvaluationSummary` 字段固定为：

```java
int gameCount
double score
double winRate
double averageRounds
long decisionP95Ms
int invalidMoveCount
java.util.Map<String, Integer> failureCounts
```

`EvaluationMatchResult` 字段固定为：

```java
String opponentKey
long mapSeed
String side
String result
int rounds
long decisionP95Ms
int invalidMoveCount
String failureReason
String replay
```

Replay 编码为 `aDirection,bDirection;aDirection,bDirection`，并在超过上限时标记 `REPLAY_LIMIT`，不得返回超大内容。

- [ ] **步骤 4：实现评测循环**

配置：

```properties
kob.bot.evaluation.public-seeds=101,211,307,401,503,601,709,809
kob.bot.evaluation.hidden-seeds=907,1009,1103,1201
kob.bot.evaluation.max-rounds=1000
kob.bot.evaluation.batch-timeout-ms=120000
kob.bot.evaluation.hidden-timeout-ms=60000
kob.bot.evaluation.replay-limit-bytes=1048576
```

循环顺序固定为种子、基准策略 `safe/greedy/territory`、出生侧 `A/B`。每个版本只创建一个 `PersistentBotProcess`。

候选策略适配器：

```java
Strategy candidate = snapshot -> {
    try {
        return process.decide(snapshotInputCodec.encode(snapshot)).getDirection();
    } catch (SandboxExecutionException error) {
        if (error.getCode() == SandboxErrorCode.STEP_TIMEOUT) {
            throw new StrategyExecutionException(FailureReason.STEP_TIMEOUT, error.getMessage());
        }
        if (error.getCode() == SandboxErrorCode.OUTPUT_LIMIT) {
            throw new StrategyExecutionException(FailureReason.OUTPUT_LIMIT, error.getMessage());
        }
        throw new StrategyExecutionException(FailureReason.SANDBOX_VIOLATION, error.getMessage());
    }
};
```

候选为 A 时读取 `GameResult.getDecisionNanosA()`；候选为 B 时读取 `getDecisionNanosB()`。

- [ ] **步骤 5：运行协调器测试**

运行：

```bash
cd backendcloud
mvn -pl botrunningsystem -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=EvaluationCoordinatorTest test
```

预期：公开 48 局、隐藏 24 局、交换出生位置、平局计分和 P95 测试通过。

- [ ] **步骤 6：提交**

```bash
git add backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation \
  backendcloud/botrunningsystem/src/test/java/com/kob/service/evaluation \
  backendcloud/botrunningsystem/src/main/resources/application.properties
git commit -m "feat(离线评测): 用可信规则聚合固定数据集指标"
```

### 任务 5：提供内部 HTTP API、幂等和取消

**文件：**
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/EvaluationJobRegistry.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/EvaluationService.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/impl/EvaluationServiceImpl.java`
- 创建：`backendcloud/botrunningsystem/src/main/java/com/kob/controller/EvaluationController.java`
- 修改：`backendcloud/botrunningsystem/src/main/java/com/kob/config/SecurityConfig.java`
- 测试：`backendcloud/botrunningsystem/src/test/java/com/kob/service/evaluation/EvaluationServiceImplTest.java`
- 测试：`backendcloud/botrunningsystem/src/test/java/com/kob/controller/EvaluationControllerTest.java`

**接口：**
- 产出：

```text
POST /bot/evaluate/
POST /bot/evaluate/{requestId}/cancel/
```

- [ ] **步骤 1：编写失败测试**

Service 测试：

```java
EvaluationResponse first = service.evaluate(request);
EvaluationResponse second = service.evaluate(request);
assertSame(first, second);
verify(coordinator, times(1)).evaluate(request);
```

取消测试：

```java
registry.register("req-1", process);
assertTrue(service.cancel("req-1"));
verify(process).cancel();
assertFalse(registry.contains("req-1"));
```

Controller 测试使用 `MockMvc`，断言本地 POST 返回 JSON，缺少 `requestId` 或源码返回 HTTP 400。

- [ ] **步骤 2：确认测试失败**

运行：

```bash
cd backendcloud
mvn -pl botrunningsystem -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=EvaluationServiceImplTest,EvaluationControllerTest \
  test
```

预期：缺少 Service 或 Controller。

- [ ] **步骤 3：实现幂等注册表**

`EvaluationJobRegistry` 使用两个 `ConcurrentHashMap`：

```java
Map<String, PersistentBotProcess> running
Map<String, EvaluationResponse> completed
```

规则：

- 已完成 `requestId` 直接返回保存的响应。
- 同一 `requestId` 正在执行时返回错误码 `REQUEST_IN_PROGRESS`，不得启动第二个进程。
- 协调器成功后先保存响应，再移除 running。
- 失败后移除 running，不缓存失败。
- 取消不存在的请求返回 `false`，不抛异常。

- [ ] **步骤 4：实现 Controller 与本地安全规则**

```java
@PostMapping("/bot/evaluate/")
public EvaluationResponse evaluate(@Valid @RequestBody EvaluationRequest request)
```

```java
@PostMapping("/bot/evaluate/{requestId}/cancel/")
public Map<String, Object> cancel(@PathVariable String requestId)
```

`SecurityConfig`：

```java
.antMatchers("/bot/add/", "/bot/evaluate/**").hasIpAddress("127.0.0.1")
```

保留 `OPTIONS` 放行和其他请求鉴权规则。

- [ ] **步骤 5：运行 API 测试和模块全测**

运行：

```bash
cd backendcloud
mvn -pl botrunningsystem -am test
```

预期：全部通过，现有 `BotPoolTest`、`ProcessSandboxBotExecutorTest` 和 `JooprBotExecutorTest` 无回归。

- [ ] **步骤 6：提交**

```bash
git add backendcloud/botrunningsystem/src/main/java/com/kob/controller/EvaluationController.java \
  backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation \
  backendcloud/botrunningsystem/src/main/java/com/kob/config/SecurityConfig.java \
  backendcloud/botrunningsystem/src/test
git commit -m "feat(评测接口): 支持幂等批量执行与主动取消"
```

### 任务 6：阶段验收

**文件：**
- 不新增业务文件。

**接口：**
- 产出：阶段 3 可调用的稳定内部 HTTP API。

- [ ] **步骤 1：运行全部 Java 测试**

```bash
cd backendcloud
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home mvn test
```

预期：全部模块通过。

- [ ] **步骤 2：检查候选进程不可见数据**

运行：

```bash
rg -n "hidden-seeds|EvaluationMode|opponentKey|mapSeed" \
  backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/sandbox \
  backendcloud/botrunningsystem/src/main/java/com/kob/service/evaluation/protocol
```

预期：沙箱和协议包中不出现隐藏种子配置、数据集类型或对手名称。

- [ ] **步骤 3：重复运行资源清理测试**

```bash
cd backendcloud
for i in 1 2 3; do
  JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home \
    mvn -q -pl botrunningsystem -am \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dtest=PersistentBotProcessTest,PersistentSandboxSecurityTest \
    test || exit 1
done
```

预期：3 次均通过；`/tmp` 中没有测试创建的 `kob-evaluation-*` 残留。

- [ ] **步骤 4：检查差异**

```bash
git diff --check
git status --short
```

预期：无空白错误，未覆盖用户原有前端修改。

- [ ] **步骤 5：记录阶段结果**

最后一个阶段提交必须包含：

```text
Tested: botrunningsystem 全量测试、backendcloud 全量测试、恶意 Bot 与资源清理测试重复 3 次
Not-tested: backend Agent Workflow 尚未接入内部评测 API
Directive: 隐藏种子只允许存在于可信评测配置，禁止进入沙箱协议或模型上下文
```
