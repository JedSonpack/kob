# 重构计划：注册服务测试护栏 + 空密码空指针修复（审计任务 0.1 + 1.4）

> 依据：`docs/architecture/legacy-project-audit.md` 第 7 节路线图推荐的**首个实施模块**。
> 原则：每次一个小模块，独立提交、可单独回滚，不混入其他关注点。
> 创建日期：2026-07-11

## 1. 范围
- 仅覆盖审计任务 **0.1（注册服务回归测试骨架）** 与 **1.4（注册空密码 NPE 修复）**。
- 不改 API、不改数据库、不动其他服务、不升级依赖。
- 后续模块（1.1 配置外置、1.2 Bot 归属校验、1.3 BotPool 锁等）待本批落地验证后再逐一推进。

## 2. 现状核实（已读源码确认）
- `RegisterServiceImpl.register()` 第 34-36 行：`password == null || confirmedPassword == null` 时只 `put` 错误信息、**未 `return`**，随后第 45 行 `password.length()` 抛 NPE；`confirmedPassword == null` 同理。
- 仓库**零测试**；`spring-boot-starter-test`（含 JUnit 5 + Mockito 3.6.x）已在 `backend/pom.xml`。
- Maven：`/Users/liheng/project/Java/java_env/maven/apache-maven-3.8.3/bin/mvn`，运行于 Java 17，alimaven 镜像覆盖 central；测试依赖首次需下载。

## 3. 测试策略
- **纯 Mockito 单元测试**：不加载 Spring 上下文、不连数据库。
- `@Mock` 桩接 `UserMapper`、`PasswordEncoder`；`@InjectMocks` 构造 `RegisterServiceImpl`。
- 只 mock 接口（`UserMapper`、`PasswordEncoder`），规避 Java 17 + Mockito 3.x 的字节码插桩风险。
- 测试路径：`backendcloud/backend/src/test/java/com/kob/backend/service/impl/user/account/RegisterServiceImplTest.java`。

## 4. 提交 1（任务 0.1）：注册服务回归测试骨架
覆盖**当前已正确**的行为，全部 GREEN，锁定既有契约：

| 用例 | 输入 | 期望 error_message |
| --- | --- | --- |
| username 为 null | `null, "p", "p"` | `用户名不能为空` |
| username 去空格后空 | `"   ", "p", "p"` | `用户名不能为空` |
| username 过长 | 101 字符 | `用户名长度不能大于100` |
| password 空串 | `"u", "", ""` | `密码不能为空` |
| 密码过长 | 101 字符 | `密码长度不能大于100` |
| 两次密码不一致 | `"u","p1","p2"` | `两次输入的密码不一致` |
| 用户名已存在 | selectList 返回非空 | `用户名已存在`，且不调用 `insert` |
| 注册成功 | selectList 空 | `success`，验证 `encode` 被调用、`insert` 调用一次且携带编码密码与 rating=1500 |

验证：`mvn -pl backend -am test` 全绿。

## 5. 提交 2（任务 1.4）：TDD 修复空密码空指针
1. **RED**：测试类新增两用例，断言 `password == null`、`confirmedPassword == null` 时返回 `密码不能为空`；运行确认因 NPE 失败。
2. **GREEN**：在 `RegisterServiceImpl.java` 第 36 行 null 检查块末尾补 `return res;`；重跑全绿。
3. 不改动其他逻辑（最小修改）。

验证：`mvn -pl backend -am test`，含新增 null 用例共 10 个全绿。

## 6. 验证命令
```bash
MVN=/Users/liheng/project/Java/java_env/maven/apache-maven-3.8.3/bin/mvn
cd backendcloud && "$MVN" -pl backend -am test
```
首次会经 aliyun 镜像下载测试依赖（一次性）。

## 7. 风险与 contingency
- Mockito 3.6.28 + Java 17：纯接口 mock 不触发插桩；若仍异常，则在 backend pom 的 surefire 加 `--add-opens` argLine，**单独提交**，不混入本批。
- aliyun 镜像为 http：`maven-default-http-blocker` 在 settings.xml 已注释，HTTP 放行。

## 8. 提交与分支约定
- 在当前 **master** 分支开发（遵循 CLAUDE.md「当前分支」约定与仓库历史习惯）。
- 中文提交信息，匹配仓库既有风格；提交正文按审计 7.1 模板简述：修改原因 / 影响范围 / 行为约束 / 验证 / 回滚 / 未验证项。
- **提交前征求确认**；若你希望走独立分支而非 master，批准时说明。

## 9. 回滚方式
- 提交 1：删除新增测试文件与 `src/test` 目录即可。
- 提交 2：单文件 `RegisterServiceImpl.java` 回退（去掉新增的 `return`）。

## 10. 未验证项
- 未做端到端 / 控制器层 / 数据库集成测试（本批次仅服务逻辑单元测试）。
- 未验证 Spring 上下文加载与 Java 17 运行时全链路（纯单测规避，后续集成测试任务再覆盖）。
