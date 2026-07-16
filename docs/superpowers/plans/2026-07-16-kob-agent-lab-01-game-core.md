# KOB Agent Lab 阶段 1：确定性 Game Core 实施计划

> **面向智能体工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐项实施此计划。步骤使用复选框（`- [ ]`）语法跟踪。

**目标：** 抽取一个不依赖 Spring、WebSocket、数据库或 HTTP 的 `game-core` 模块，让在线对战和离线评测共用确定性地图、蛇增长、碰撞和胜负规则。

**架构：** `game-core` 使用不可变快照和同步 `GameEngine` 推进单局比赛。现有 `backend.websocket.utils.Game` 继续承担线程等待、Bot 回调、WebSocket 推送和结果持久化，但地图生成与每回合规则委托给 `game-core`。

**技术栈：** Java 8、Maven 多模块、JUnit 5、Spring Boot 2.4.5 依赖管理

## 全局约束

- `game-core` 不得依赖 Spring、Fastjson、MyBatis、Lombok、WebSocket、HTTP 或数据库。
- 不改变现有方向编码：`0=上`、`1=右`、`2=下`、`3=左`。
- 不改变蛇增长规则：前 10 回合每回合增长，之后 `step % 3 == 1` 时增长。
- 不改变现有头对头语义：碰撞检查忽略对手蛇头，双方蛇头同格时本回合不因头对头结束。
- 地图必须中心对称、出生点可用且两个出生点连通。
- 相同 `GameConfig`、种子和策略必须产生完全一致的 `GameResult` 与 Replay。
- 在线 `Game` 的 `gameId`、`roundId`、等待时序、消息顺序和积分持久化行为保持不变。
- 不删除现有 `backend.websocket.utils.Cell`、`Player` 或 `GameRules`；首阶段使用适配器降低回归范围。
- 执行本计划 Maven 命令前先运行：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home
export PATH="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin:$PATH"
```

---

## 文件结构

### 创建

- `backendcloud/game-core/pom.xml`：纯 Java 模块依赖声明。
- `backendcloud/game-core/src/main/java/com/kob/game/core/Direction.java`：方向校验与位移。
- `backendcloud/game-core/src/main/java/com/kob/game/core/Position.java`：不可变坐标。
- `backendcloud/game-core/src/main/java/com/kob/game/core/SnakeState.java`：出生点、历史移动和蛇身计算。
- `backendcloud/game-core/src/main/java/com/kob/game/core/GameSnapshot.java`：只读局面。
- `backendcloud/game-core/src/main/java/com/kob/game/core/GameConfig.java`：地图和比赛上限。
- `backendcloud/game-core/src/main/java/com/kob/game/core/Strategy.java`：策略接口。
- `backendcloud/game-core/src/main/java/com/kob/game/core/StrategyExecutionException.java`：策略执行失败的可信分类。
- `backendcloud/game-core/src/main/java/com/kob/game/core/GameEngine.java`：单局接口。
- `backendcloud/game-core/src/main/java/com/kob/game/core/GameResult.java`：胜负、回合、Replay 和延迟。
- `backendcloud/game-core/src/main/java/com/kob/game/core/MatchOutcome.java`：`A_WIN`、`B_WIN`、`DRAW`、`TIMEOUT`。
- `backendcloud/game-core/src/main/java/com/kob/game/core/FailureReason.java`：失败原因分类。
- `backendcloud/game-core/src/main/java/com/kob/game/core/ReplayFrame.java`：单回合双方方向。
- `backendcloud/game-core/src/main/java/com/kob/game/core/CoreGameRules.java`：增长、蛇身和碰撞纯函数。
- `backendcloud/game-core/src/main/java/com/kob/game/core/DeterministicMapGenerator.java`：固定种子地图。
- `backendcloud/game-core/src/main/java/com/kob/game/core/DefaultGameEngine.java`：同步确定性比赛引擎。
- `backendcloud/game-core/src/main/java/com/kob/game/core/strategy/SafeBot.java`
- `backendcloud/game-core/src/main/java/com/kob/game/core/strategy/GreedyBot.java`
- `backendcloud/game-core/src/main/java/com/kob/game/core/strategy/TerritoryBot.java`
- 对应 `backendcloud/game-core/src/test/java/com/kob/game/core/**` 测试。

### 修改

- `backendcloud/pom.xml`：注册 `game-core` 模块。
- `backendcloud/backend/pom.xml`：依赖 `com.kob:game-core:${project.version}`。
- `backendcloud/backend/src/main/java/com/kob/backend/websocket/utils/GameRules.java`：委托 `CoreGameRules`。
- `backendcloud/backend/src/main/java/com/kob/backend/websocket/utils/Game.java`：委托地图生成和回合胜负判断。
- `backendcloud/backend/src/test/java/com/kob/backend/websocket/utils/GameRulesTest.java`：保留兼容回归。
- `backendcloud/backend/src/test/java/com/kob/backend/websocket/utils/GameProtocolTest.java`：锁定消息顺序。

---

### 任务 1：建立 `game-core` 模块和固定公共接口

**文件：**
- 创建：`backendcloud/game-core/pom.xml`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/Direction.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/Position.java`
- 修改：`backendcloud/pom.xml`
- 修改：`backendcloud/backend/pom.xml`
- 测试：`backendcloud/game-core/src/test/java/com/kob/game/core/DirectionTest.java`

**接口：**
- 消费：父 POM 的 Java 8 与 Spring Boot 依赖管理。
- 产出：`Direction.isValid(int)`、`Direction.move(Position,int)` 和可被其他模块依赖的 `game-core` JAR。

- [ ] **步骤 1：编写失败测试**

```java
package com.kob.game.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectionTest {
    @Test
    void validatesOnlyZeroToThree() {
        assertFalse(Direction.isValid(-1));
        for (int direction = 0; direction < 4; direction++) {
            assertTrue(Direction.isValid(direction));
        }
        assertFalse(Direction.isValid(4));
    }

    @Test
    void movesWithExistingDirectionEncoding() {
        Position start = new Position(5, 6);
        assertEquals(new Position(4, 6), Direction.move(start, 0));
        assertEquals(new Position(5, 7), Direction.move(start, 1));
        assertEquals(new Position(6, 6), Direction.move(start, 2));
        assertEquals(new Position(5, 5), Direction.move(start, 3));
    }
}
```

- [ ] **步骤 2：运行测试，验证模块尚未注册**

运行：

```bash
cd backendcloud
mvn -pl game-core test
```

预期：失败并显示找不到 `game-core` 模块或相关类型。

- [ ] **步骤 3：创建模块与最小类型**

`backendcloud/game-core/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.kob</groupId>
        <artifactId>backendcloud</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>game-core</artifactId>
    <packaging>jar</packaging>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

`Direction.java`：

```java
package com.kob.game.core;

public final class Direction {
    private static final int[] DX = {-1, 0, 1, 0};
    private static final int[] DY = {0, 1, 0, -1};

    private Direction() {}

    public static boolean isValid(int direction) {
        return direction >= 0 && direction < 4;
    }

    public static Position move(Position position, int direction) {
        if (!isValid(direction)) {
            throw new IllegalArgumentException("direction must be between 0 and 3");
        }
        return new Position(
                position.getRow() + DX[direction],
                position.getCol() + DY[direction]
        );
    }
}
```

`Position.java`：

```java
package com.kob.game.core;

import java.util.Objects;

public final class Position {
    private final int row;
    private final int col;

    public Position(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof Position)) return false;
        Position other = (Position) value;
        return row == other.row && col == other.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }
}
```

- [ ] **步骤 4：运行模块测试**

运行：

```bash
cd backendcloud
mvn -pl game-core test
```

预期：`DirectionTest` 通过。

- [ ] **步骤 5：提交**

```bash
git add backendcloud/pom.xml backendcloud/backend/pom.xml backendcloud/game-core
git commit -m "feat(规则内核): 建立在线与离线共用的类型边界"
```

### 任务 2：迁移蛇增长、蛇身和碰撞规则

**文件：**
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/SnakeState.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/CoreGameRules.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/FailureReason.java`
- 测试：`backendcloud/game-core/src/test/java/com/kob/game/core/CoreGameRulesTest.java`

**接口：**
- 消费：`Position`、`Direction.move(Position,int)`。
- 产出：`CoreGameRules.isGrowing(int)`、`CoreGameRules.cells(SnakeState)`、`CoreGameRules.failureReason(...)`、`CoreGameRules.isAlive(...)`。

- [ ] **步骤 1：编写失败测试**

```java
package com.kob.game.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreGameRulesTest {
    @Test
    void keepsExistingGrowthSchedule() {
        assertTrue(CoreGameRules.isGrowing(1));
        assertTrue(CoreGameRules.isGrowing(10));
        assertFalse(CoreGameRules.isGrowing(11));
        assertFalse(CoreGameRules.isGrowing(12));
        assertTrue(CoreGameRules.isGrowing(13));
    }

    @Test
    void buildsSnakeCellsFromStartAndMoves() {
        SnakeState snake = new SnakeState(
                new Position(5, 1),
                Arrays.asList(0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2)
        );
        assertEquals(11, CoreGameRules.cells(snake).size());
    }

    @Test
    void ignoresOpponentHeadLikeOnlineRules() {
        int[][] map = new int[5][5];
        assertTrue(CoreGameRules.isAlive(
                Arrays.asList(new Position(2, 1), new Position(2, 2)),
                Arrays.asList(new Position(2, 3), new Position(2, 2)),
                map
        ));
        assertTrue(CoreGameRules.isAlive(
                Collections.singletonList(new Position(1, 1)),
                Collections.singletonList(new Position(3, 3)),
                map
        ));
    }

    @Test
    void classifiesWallSelfAndOpponentBody() {
        int[][] map = new int[5][5];
        map[0][2] = 1;
        assertEquals(FailureReason.WALL, CoreGameRules.failureReason(
                Arrays.asList(new Position(1, 2), new Position(0, 2)),
                Arrays.asList(new Position(4, 4), new Position(4, 3)),
                map
        ));
        assertEquals(FailureReason.SELF, CoreGameRules.failureReason(
                Arrays.asList(new Position(2, 2), new Position(2, 3), new Position(2, 2)),
                Arrays.asList(new Position(4, 4), new Position(4, 3)),
                new int[5][5]
        ));
        assertEquals(FailureReason.OPPONENT_BODY, CoreGameRules.failureReason(
                Arrays.asList(new Position(0, 0), new Position(1, 1)),
                Arrays.asList(new Position(1, 1), new Position(2, 2)),
                new int[5][5]
        ));
    }
}
```

- [ ] **步骤 2：确认测试失败**

运行：

```bash
cd backendcloud
mvn -pl game-core -Dtest=CoreGameRulesTest test
```

预期：编译失败，缺少 `SnakeState` 和 `CoreGameRules`。

- [ ] **步骤 3：实现规则**

```java
package com.kob.game.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SnakeState {
    private final Position start;
    private final List<Integer> moves;

    public SnakeState(Position start, List<Integer> moves) {
        this.start = start;
        this.moves = Collections.unmodifiableList(new ArrayList<>(moves));
    }

    public Position getStart() { return start; }
    public List<Integer> getMoves() { return moves; }

    public SnakeState append(int direction) {
        List<Integer> copy = new ArrayList<>(moves);
        copy.add(direction);
        return new SnakeState(start, copy);
    }
}
```

```java
package com.kob.game.core;

import java.util.ArrayList;
import java.util.List;

public final class CoreGameRules {
    private CoreGameRules() {}

    public static boolean isGrowing(int step) {
        return step <= 10 || step % 3 == 1;
    }

    public static List<Position> cells(SnakeState snake) {
        List<Position> result = new ArrayList<>();
        Position current = snake.getStart();
        result.add(current);
        int step = 0;
        for (Integer direction : snake.getMoves()) {
            current = Direction.move(current, direction);
            result.add(current);
            if (!isGrowing(++step)) {
                result.remove(0);
            }
        }
        return result;
    }

    public static boolean isAlive(
            List<Position> self,
            List<Position> opponent,
            int[][] map
    ) {
        return failureReason(self, opponent, map) == FailureReason.NONE;
    }

    public static FailureReason failureReason(
            List<Position> self,
            List<Position> opponent,
            int[][] map
    ) {
        Position head = self.get(self.size() - 1);
        if (map[head.getRow()][head.getCol()] == 1) return FailureReason.WALL;
        for (int i = 0; i < self.size() - 1; i++) {
            if (self.get(i).equals(head)) return FailureReason.SELF;
        }
        for (int i = 0; i < opponent.size() - 1; i++) {
            if (opponent.get(i).equals(head)) return FailureReason.OPPONENT_BODY;
        }
        return FailureReason.NONE;
    }
}
```

对手循环必须排除对手蛇头，与现有在线规则保持一致；只检查对手身体。

`FailureReason` 固定为：

```java
NONE, WALL, SELF, OPPONENT_BODY, INVALID_MOVE,
STEP_TIMEOUT, SANDBOX_VIOLATION, OUTPUT_LIMIT, ROUND_LIMIT
```

- [ ] **步骤 4：运行规则测试**

运行：

```bash
cd backendcloud
mvn -pl game-core -Dtest=CoreGameRulesTest test
```

预期：全部通过。

- [ ] **步骤 5：提交**

```bash
git add backendcloud/game-core
git commit -m "feat(规则内核): 固化蛇增长与碰撞语义"
```

### 任务 3：实现固定种子对称地图

**文件：**
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/GameConfig.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/DeterministicMapGenerator.java`
- 测试：`backendcloud/game-core/src/test/java/com/kob/game/core/DeterministicMapGeneratorTest.java`

**接口：**
- 产出：`int[][] DeterministicMapGenerator.generate(GameConfig config)`。
- `GameConfig` 构造器固定为 `GameConfig(int rows, int cols, int innerWalls, long seed, int maxRounds)`。

- [ ] **步骤 1：编写失败测试**

测试必须断言：

```java
GameConfig config = new GameConfig(13, 14, 20, 2026071601L, 1000);
int[][] first = generator.generate(config);
int[][] second = generator.generate(config);
assertArrayEquals(first, second);
assertEquals(first[r][c], first[12 - r][13 - c]);
assertEquals(0, first[11][1]);
assertEquals(0, first[1][12]);
assertTrue(generator.isConnected(first, new Position(11, 1), new Position(1, 12)));
```

还必须测试不同种子至少产生一个不同单元格。

- [ ] **步骤 2：确认测试失败**

运行：

```bash
cd backendcloud
mvn -pl game-core -Dtest=DeterministicMapGeneratorTest test
```

预期：缺少生成器或确定性断言失败。

- [ ] **步骤 3：实现生成器**

实现规则：

```text
1. 先写四周边界墙。
2. 使用 new Random(config.getSeed())。
3. 每次同时放置 (r,c) 与中心对称位置。
4. 跳过出生点、已有墙和同一中心点。
5. 最多尝试 1000 次生成完整地图。
6. 每次完整生成后用队列 BFS 检查两个出生点连通。
7. 1000 次都失败时抛 IllegalStateException，消息包含 seed。
8. 返回深拷贝，不暴露内部数组。
```

`isConnected` 使用 `ArrayDeque<Position>`，不得递归修改原地图。

- [ ] **步骤 4：运行地图测试**

运行：

```bash
cd backendcloud
mvn -pl game-core -Dtest=DeterministicMapGeneratorTest test
```

预期：固定种子、对称、出生点和连通性测试全部通过。

- [ ] **步骤 5：提交**

```bash
git add backendcloud/game-core
git commit -m "feat(规则内核): 提供可复现的对称地图生成"
```

### 任务 4：实现同步单局引擎和 Replay

**文件：**
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/GameSnapshot.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/Strategy.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/StrategyExecutionException.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/GameEngine.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/MatchOutcome.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/ReplayFrame.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/GameResult.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/DefaultGameEngine.java`
- 测试：`backendcloud/game-core/src/test/java/com/kob/game/core/DefaultGameEngineTest.java`

**接口：**
- 消费：`GameConfig`、`Strategy`、`CoreGameRules`、`DeterministicMapGenerator`。
- 产出：`Strategy.nextMove(GameSnapshot)`、`GameEngine.play(GameConfig,Strategy,Strategy)` 和确定性的 `GameResult`。

- [ ] **步骤 1：编写失败测试**

测试使用两个固定策略：

```java
Strategy down = snapshot -> 2;
Strategy up = snapshot -> 0;
GameConfig config = new GameConfig(13, 14, 0, 7L, 20);

GameResult first = new DefaultGameEngine().play(config, down, up);
GameResult second = new DefaultGameEngine().play(config, down, up);

assertEquals(MatchOutcome.DRAW, first.getOutcome());
assertEquals(first.getReplay(), second.getReplay());
assertEquals(first.getRounds(), second.getRounds());
```

另写测试覆盖：

- 策略返回 `-1` 时对应一方判负并增加非法移动计数。
- 达到 `maxRounds` 时返回 `TIMEOUT`。
- `GameSnapshot` 返回地图深拷贝和不可修改的移动列表。

- [ ] **步骤 2：确认测试失败**

运行：

```bash
cd backendcloud
mvn -pl game-core -Dtest=DefaultGameEngineTest test
```

预期：缺少引擎结果类型。

- [ ] **步骤 3：实现不可变快照**

`GameSnapshot` 必须包含：

```java
int round
int[][] map
SnakeState self
SnakeState opponent
```

构造器和 Getter 均进行防御性复制。快照不得暴露种子、基准策略名称或最终胜负。

`StrategyExecutionException`：

```java
package com.kob.game.core;

public final class StrategyExecutionException extends RuntimeException {
    private final FailureReason failureReason;

    public StrategyExecutionException(FailureReason failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }
}
```

- [ ] **步骤 4：实现引擎循环**

每回合顺序固定为：

```text
1. 为 A、B 分别创建自身视角 GameSnapshot。
2. 顺序调用 A、B Strategy，并记录 System.nanoTime() 耗时。
3. 非法方向记入对应 invalidMoveCount，并直接判负。
4. 把两个方向同时追加到不可变 SnakeState。
5. 分别计算蛇身。
6. 分别调用 CoreGameRules.isAlive。
7. 先追加 ReplayFrame，再生成胜负。
8. 双方都死亡为 DRAW；只有 A 死为 B_WIN；只有 B 死为 A_WIN。
9. 达到 maxRounds 仍未死亡为 TIMEOUT。
```

`GameResult` 必须保存：

```java
MatchOutcome outcome
int rounds
List<ReplayFrame> replay
List<Long> decisionNanosA
List<Long> decisionNanosB
int invalidMoveCountA
int invalidMoveCountB
FailureReason failureReasonA
FailureReason failureReasonB
```

`StrategyExecutionException` 只包含 `FailureReason` 和脱敏消息。引擎捕获该异常并让对应一方失败；非法方向设置 `INVALID_MOVE`；达到最大回合设置双方 `ROUND_LIMIT`。

- [ ] **步骤 5：运行引擎测试**

运行：

```bash
cd backendcloud
mvn -pl game-core -Dtest=DefaultGameEngineTest test
```

预期：确定性、非法移动、超时和不可变快照测试通过。

- [ ] **步骤 6：提交**

```bash
git add backendcloud/game-core
git commit -m "feat(规则内核): 生成确定性单局结果与回放"
```

### 任务 5：实现 3 个确定性基准策略

**文件：**
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/strategy/SafeBot.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/strategy/GreedyBot.java`
- 创建：`backendcloud/game-core/src/main/java/com/kob/game/core/strategy/TerritoryBot.java`
- 测试：`backendcloud/game-core/src/test/java/com/kob/game/core/strategy/BaselineStrategyTest.java`

**接口：**
- 消费：`GameSnapshot`。
- 产出：3 个无随机数、无外部状态的 `Strategy` 实现。

- [ ] **步骤 1：编写失败测试**

构造一个只有方向 `1` 安全的快照，断言 3 个策略都返回 `1`。再构造两个方向都安全但空间不同的快照，断言：

```java
assertEquals(0, new SafeBot().nextMove(snapshotWithUpAndRightSafe));
assertEquals(1, new GreedyBot().nextMove(snapshotWithRightLarger));
assertEquals(1, new TerritoryBot().nextMove(snapshotWithRightTerritoryAdvantage));
```

连续调用 20 次必须返回同一方向。

- [ ] **步骤 2：确认测试失败**

运行：

```bash
cd backendcloud
mvn -pl game-core -Dtest=BaselineStrategyTest test
```

预期：缺少基准策略。

- [ ] **步骤 3：实现策略**

- `SafeBot`：按 `0,1,2,3` 顺序返回第一个不会立即碰撞的方向；无安全方向返回 `0`。
- `GreedyBot`：对每个合法方向执行 BFS，选择下一步可达空格数量最大的方向；同分选择方向数字较小者。
- `TerritoryBot`：分别从双方下一步蛇头执行多源距离 BFS，计算更接近己方的单元格数量，选择差值最大的方向；同分依次比较己方空间、方向数字。

3 个策略只读取快照，不修改地图，不使用随机数和系统时间。

- [ ] **步骤 4：运行策略和全模块测试**

运行：

```bash
cd backendcloud
mvn -pl game-core test
```

预期：全部通过。

- [ ] **步骤 5：提交**

```bash
git add backendcloud/game-core
git commit -m "feat(评测基线): 增加三种确定性对手策略"
```

### 任务 6：让在线对战委托 `game-core`

**文件：**
- 修改：`backendcloud/backend/src/main/java/com/kob/backend/websocket/utils/GameRules.java`
- 修改：`backendcloud/backend/src/main/java/com/kob/backend/websocket/utils/Game.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/websocket/utils/GameRulesTest.java`
- 测试：`backendcloud/backend/src/test/java/com/kob/backend/websocket/utils/GameProtocolTest.java`
- 创建：`backendcloud/backend/src/test/java/com/kob/backend/websocket/utils/GameMapCompatibilityTest.java`

**接口：**
- 消费：`CoreGameRules`、`DeterministicMapGenerator`、`GameConfig`。
- 产出：现有在线 API 与 WebSocket 协议不变。

- [ ] **步骤 1：增加在线兼容失败测试**

`GameMapCompatibilityTest` 必须：

```java
Game first = new Game(13, 14, 20, 1, null, 2, null, 99L);
Game second = new Game(13, 14, 20, 1, null, 2, null, 99L);
first.createMap();
second.createMap();
assertArrayEquals(first.getG(), second.getG());
```

为 `Game` 增加包级可见或公共的带 `seed` 构造器，仅用于确定性创建；原构造器保留并调用 `new Random().nextLong()`。

- [ ] **步骤 2：确认兼容测试失败**

运行：

```bash
cd backendcloud
mvn -pl backend -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=GameMapCompatibilityTest test
```

预期：带种子构造器不存在。

- [ ] **步骤 3：委托地图和规则**

`GameRules` 保留原签名，并把 `Cell` 转为 `Position`：

```java
public static boolean checkTailIncreasing(int step) {
    return CoreGameRules.isGrowing(step);
}

public static boolean checkValid(List<Cell> self, List<Cell> opponent, int[][] map) {
    return CoreGameRules.isAlive(toPositions(self), toPositions(opponent), map);
}
```

`Game.createMap()` 使用 `DeterministicMapGenerator.generate(...)`，再逐行复制到现有 `g` 数组，避免更改 `getG()` 契约。

删除 `Game` 内部旧的递归连通检查和随机画图实现；不要修改等待移动、回调校验、消息广播和持久化代码。

- [ ] **步骤 4：运行后端规则与协议测试**

运行：

```bash
cd backendcloud
mvn -pl backend -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=GameRulesTest,GameMapCompatibilityTest,GameProtocolTest,PkValidationTest \
  test
```

预期：全部通过，`fatalRound_broadcastsMoveBeforeResult` 仍证明先广播 `move` 再广播 `result`。

- [ ] **步骤 5：运行全后端测试**

运行：

```bash
cd backendcloud
mvn test
```

预期：全部模块测试通过。

- [ ] **步骤 6：提交**

```bash
git add backendcloud/pom.xml backendcloud/backend/pom.xml \
  backendcloud/backend/src/main/java/com/kob/backend/websocket/utils \
  backendcloud/backend/src/test/java/com/kob/backend/websocket/utils
git commit -m "refactor(在线对战): 复用确定性规则内核且保持协议兼容"
```

### 任务 7：阶段验收

**文件：**
- 不新增业务文件。
- 验证：`backendcloud/game-core/**`、`backendcloud/backend/**`。

**接口：**
- 产出：阶段 2 可以稳定消费的 `game-core` JAR 与公共接口。

- [ ] **步骤 1：运行静态边界检查**

运行：

```bash
rg -n "org\\.springframework|com\\.alibaba|com\\.baomidou|javax\\.websocket" \
  backendcloud/game-core/src/main/java
```

预期：无输出。

- [ ] **步骤 2：运行重复性测试**

运行：

```bash
cd backendcloud
for i in 1 2 3; do mvn -q -pl game-core test || exit 1; done
```

预期：3 次均退出码 `0`。

- [ ] **步骤 3：运行现有后端回归**

运行：

```bash
cd backendcloud
mvn test
```

预期：全部测试通过，无现有测试减少。

- [ ] **步骤 4：检查改动范围**

运行：

```bash
git diff --check
git status --short
```

预期：无空白错误；用户原有未提交文件仍存在且未被回退。

- [ ] **步骤 5：记录阶段结果**

最后一个阶段提交的 Lore trailers 必须记录：

```text
Tested: game-core 全量测试、backend 全量测试、确定性重复运行 3 次
Not-tested: 完整浏览器对战由阶段 4 统一执行
Directive: game-core 公共接口已供评测器消费，后续改名必须同时迁移阶段 2 和阶段 3
```
