# KOB 重构进度看板

> 路线图来源：`docs/architecture/legacy-project-audit.md` 第 7 节。
> 增量原则：每次一个小模块，独立提交、可回滚。
> 更新日期：2026-07-11

**进度：19 / 20 任务已完成**（master 本地领先 origin 9 个提交，5.2 未 push；仅剩 5.3 已决策暂缓）

## 任务状态

图例：✅ 已完成　⏳ 下一个候选　⬜ 待做　🚫 需前置

| # | 任务 | 状态 | 提交 | 备注 |
| --- | --- | --- | --- | --- |
| **阶段 0：测试护栏** | | | | |
| 0.1 | 注册服务回归测试 | ✅ | `8764a68` | `RegisterServiceImplTest` 8 用例 |
| 0.2 | 游戏规则测试 | ✅ | 本批 | GameRules 抽出蛇增长+碰撞规则，6 测试 |
| 0.3 | `BotPool` 并发测试 | ✅ | `fadd34c` | 与 1.3 合并；队列排空+锁泄漏复现 |
| **阶段 1：安全止血与正确性** | | | | |
| 1.1 | 配置外置与密钥轮换 | ✅ | `7780a49` | DB/JWT 密钥外置；本地端到端验证通过 |
| 1.2 | Bot 归属和方向 0-3 校验 | ✅ | `af7d590` | P0-3+P1-1；PkValidation 8 测试 |
| 1.3 | `BotPool` 锁修复 | ✅ | `fadd34c` | run() 改标准模式，成对释放锁 |
| 1.4 | 注册空指针修复 | ✅ | `7213d66` | TDD RED→GREEN |
| **阶段 2：Bot 协议与执行** | | | | |
| 2.1 | Bot 请求关联协议 | ✅ | 本批 | gameId/roundId 防串局 |
| 2.2 | Bot 执行器接口 | ✅ | 本批 | 抽取 BotExecutor，保留 jOOR 实现 |
| 2.3 | 独立沙箱执行器 | ✅ | 本批 | 进程级隔离+SecurityManager+超时+input 隔离 |
| 2.4 | 游戏结果事务 | ✅ | 本批 | 抽取 GameResultService（@Transactional） |
| **阶段 3：前端正确性** | | | | |
| 3.1 | 前端游戏对象销毁 | ✅ | 本批 | splice 修复 + onUnmounted + Vitest |
| 3.2 | 录像详情恢复 | ✅ | 本批 | 后端按 ID 取录像 + 前端挂载拉取 |
| 3.3 | WebSocket 会话状态机 | ✅ | 本批 | safeSend + 去延时 + gameObject 防御 |
| **阶段 4：边界拆分** | | | | |
| 4.1 | 外置服务 URL 和超时 | ✅ | 本批 | RestTemplate 加超时 + 5 个 URL 外置 |
| 4.2 | 抽取连接注册表 | ✅ | 本批 | OnlineUserRegistry 分离在线连接管理 |
| 4.3 | 抽取游戏持久化与消息发布 | ✅ | 本批 | GameMessagePublisher 分离消息推送 |
| **阶段 5：工具链** | | | | |
| 5.1 | DTO 与统一校验 | ✅ | 本批 | 排行榜响应 DTO + @Min 校验 + 全局异常 |
| 5.2 | 前端统一 API Client | ✅ | 本批 | apiClient 集中 URL/认证头/错误，迁排行榜 |
| 5.3 | 依赖和工具链升级 | 🚫 | - | 已决策暂缓（需完整测试套件先行） |

## 环境与运行状态（2026-07-11 落实）

- **MySQL**：本机 8.0.26（`/usr/local/mysql`）已启动；root 密码已轮换为新值、插件 `mysql_native_password`。
- **本地密钥**：`backendcloud/backend/src/main/resources/application-local.properties`（gitignore，未入库）含轮换后的 DB 密码与 JWT 密钥。
- **DB 连接**：已验证 backend HikariCP 用外置密码连库成功（查询触达 `kob.user`）。
- **`kob` 库**：已建空库（utf8mb4）；**`user`/`bot`/`record` 表未建**。
- **JDK**：项目为 Java 8；本机全局 `settings.xml` 的 `jdk-17` profile 已去掉 `activeByDefault`（仅 JDK 17 时激活），JDK 8 下 pom 的 8 生效。
- **Bot 沙箱（2.3）**：需 botrunningsystem 跑 JDK 8（jOOR 兼容）；`kob.bot.executor=sandbox` 启用，子进程用 `kob.bot.sandbox.java-home`（默认 JDK 8 路径）、超时 `kob.bot.sandbox.timeout-ms`。

## 阻塞与 gap（审计已列，非本批任务）

- **仓库无建表脚本**：`user`/`bot`/`record` 表 DDL 未知，登录链路因此未通。需补 schema（独立工作）。
- **历史密钥仍在 Git 历史**：源码已移除并轮换，但 `git filter-repo` 重写历史未做（代价大，待评估）。
- **生产部署配置未知**：Nginx/Docker/AcWing 配置待补（审计第 8 节）。

## 已完成批次摘要

- **批 1（0.1 + 1.4）**：注册服务测试护栏 + 空密码 NPE 修复。TDD，10 测试绿。
- **批 2（1.1）**：配置外置与密钥轮换。外置 DB/JWT 密钥、`@Value` 静态 setter、local profile、轮换 runbook。12 测试绿 + 本地 DB 连库验证。
- **批 3（1.2）**：Bot 归属与方向 0-3 校验。`PkValidation` 纯函数 + 8 测试，接入 Game/WebSocketServer。20 测试绿。
- **批 4（0.3 + 1.3）**：BotPool 锁计数泄漏修复 + 并发测试。run() 改标准生产-消费模式（try/finally 成对释放），consume 移到锁外。22 测试绿。
- **批 5（4.1）**：外置服务 URL 与超时。3 个 RestTemplate 加连接/读取超时（默认 5s/3s，可配置），5 个服务间 URL 改 @Value 注入（默认本地地址）。24 测试绿。
- **批 6（2.4）**：游戏结果事务。抽取 `GameResultService`（@Transactional），原子保存双方积分与战绩；Game.saveToDatabase 委托。27 测试绿（事务回滚集成测试需 schema，未验证）。
- **批 7（2.1）**：Bot 请求关联协议。Game 增加 gameId/currentRoundId，bot 执行链路（backend↔botrunning）贯穿 gameId/roundId，回调校验匹配才应用（防串局/迟到/乱序）。31 测试绿。
- **批 8（2.2）**：Bot 执行器接口。抽取 `BotExecutor` + `JooprBotExecutor`（沿用 jOOR），Consumer 委托执行器。32 测试绿（jOOR 编译路径在 Java 17 测试环境不兼容，仅测 addUid 纯逻辑）。
- **批 9（2.3）**：进程级沙箱执行器。`SandboxMain`（子进程 + SecurityManager 禁网络/写/exec）+ `ProcessSandboxBotExecutor`（独立临时目录隔离 input.txt、超时强杀、功能开关）。JDK 8 端到端验证通过（修复 P0-1 最高安全风险 + 顺带 P1-2 input 隔离）。
- **批 10（4.2）**：抽取连接注册表。`OnlineUserRegistry` 分离在线连接管理，WebSocketServer/Game/ReceiveBotMove 改用注册表。39 测试绿。
- **批 11（4.3）**：抽取游戏消息发布。`GameMessagePublisher` 分离消息推送，Game.sendAllMessage 委托，不再直接访问连接表。backend 34 测试绿。
- **批 12（0.2）**：游戏规则测试。`GameRules` 抽出蛇增长（checkTailIncreasing）与碰撞（checkValid）纯函数，Player/Game 委托；6 测试锁定规则。backend 40 测试绿。
- **批 13（5.1）**：DTO 与统一校验。排行榜改 `UserListItemDto` 响应 DTO（不再 setPassword 规避泄漏）+ `@Min(1)` 校验 + `GlobalExceptionHandler` 统一错误。backend 41 测试绿。后端重构全部完成。
- **批 14（3.1 + Vitest）**：前端测试框架（Vitest + jsdom + @vitejs/plugin-vue）+ 游戏对象销毁修复。`AcGameObject.destroy` 的 `splice(i)` 改 `splice(idx,1)`（原会删其后全部）；GameMap 新增 `on_destroy` 清理 keydown 监听与回放定时器；GameMap.vue `onUnmounted` 销毁游戏对象。2 前端测试绿。
- **批 15（3.2）**：录像详情恢复。后端新增 `/api/record/get/` 按 ID 取单条录像；前端 `recordHelper` 共享写 Vuex 逻辑，RecordContentView 挂载时按 URL recordId 拉取（支持直达/刷新），RecordindexView 复用 helper。后端 2 + 前端 3 测试绿。
- **批 16（3.3）**：WebSocket 会话状态机。`pkSocket.safeSend` 包装发送（未连接不抛异常）；PkindexView 去掉固定 100ms 延时（立即切 playing）、move/result 防御 gameObject 未就绪；MatchGround 用 safeSend。前端 6 测试绿。
- **批 17（5.2）**：前端统一 API Client。`apiClient`（buildAjaxConfig 纯函数 + api.get/post）集中 BASE_URL、Bearer 认证头、错误处理；排行榜页迁移至 api.get。前端 8 测试绿。剩余仅 5.3（已决策暂缓）。
