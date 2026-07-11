# 重构计划：配置外置与密钥轮换（审计任务 1.1）

> 依据：`docs/architecture/legacy-project-audit.md` 第 6.1 节 P0-2、第 7 节任务 1.1。
> 目标：将数据库密码与 JWT 密钥移出源码并轮换，使已泄漏的旧值失效；不改 API。
> 创建日期：2026-07-11

## 1. 范围
- 仅外置 **DB 凭据** 与 **JWT 密钥/TTL** 两类敏感配置（审计 P0-2）。
- 不改 API、不改业务逻辑、不动 3 个服务间 URL（属任务 4.1）、不动前端地址（属 4.1/5.2）。
- 不重构 JwtUtil 的静态架构（仅外置密钥来源；架构清理留后续任务）。

## 2. 现状核实（已读源码确认）
- `JwtUtil.java:21`：`JWT_KEY = "SDFGjhdsfalshdfHFdsjkdsfds121232131afasdfac"` 硬编码，`public static final`；`JWT_TTL` 同样硬编码。`JWT_KEY`/`JWT_TTL` 仅在 JwtUtil 内部引用，调用方（`LoginServiceImpl`、`JwtAuthenticationTokenFilter`、`JwtAuthentication`/`WebSocketServer`）只调静态 `createJWT`/`parseJWT`。
- `application.properties:3`：`spring.datasource.password=123456` 硬编码；DB url/username 也硬编码。
- 另两个服务（matching/botrunning）的 `application.properties` 仅端口，无密钥。
- `.gitignore` 仅 `.omx/`、`.idea`，且未提交；无任何 `@Value`/profile 机制。

## 3. 设计决策
- **配置机制**：`application.properties`（提交，占位符，零真实密钥）+ `application-local.properties`（新增，gitignore，本地真实密钥）+ 生产用环境变量覆盖。
  - dev 默认 `spring.profiles.active=local`（便于 `mvn spring-boot:run` 直跑）；生产须显式覆盖为 `prod` 并提供环境变量。
- **JwtUtil 外置**：`@Value` 静态 setter 模式。Spring 启动时注入 `kob.jwt.secret`、`kob.jwt.ttl-millis` 到静态字段，静态方法继续用该字段。**调用方零改动**，规避 `@ServerEndpoint` 注入重构风险。标注 `// TODO 后续重构为实例注入`。
- **密钥轮换**：
  - 新 JWT secret：`openssl rand -base64 48` 生成，写入 `application-local.properties`。
  - 新 DB 密码：生成强随机密码，写入 `application-local.properties`；用户须在 MySQL 执行 `ALTER USER` 使之匹配（见 runbook）。
  - 旧泄漏值（`123456`、旧 JWT key）从源码移除；JWT 密钥变更使所有旧 Token 自动失效（用户需重新登录）。

## 4. 改动清单（单次提交，避免中间态破坏）

### 代码
- `JwtUtil.java`：移除硬编码 `JWT_KEY`/`JWT_TTL`；新增 `@Value` 静态 setter 注入 `kob.jwt.secret`、`kob.jwt.ttl-millis`；`generalKey()` 用注入值；其余静态方法签名不变。
- 新增 `JwtUtilTest.java`：往返测试（`createJWT` -> `parseJWT` 断言 subject 一致）+ 篡改 Token 解析失败断言。测试中手动调 setter 注入测试密钥。

### 配置
- `application.properties`：
  - `spring.datasource.url=${kob.db.url:jdbc:mysql://localhost:3306/kob?...}`
  - `spring.datasource.username=${kob.db.username:root}`
  - `spring.datasource.password=${kob.db.password:}`（空默认，强制外部提供）
  - 新增 `kob.jwt.secret=${kob.jwt.secret:}`、`kob.jwt.ttl-millis=${kob.jwt.ttl-millis:1209600000}`
  - 新增 `spring.profiles.active=local`（dev 默认，注释说明生产覆盖）
- 新增 `application-local.properties`（**gitignore，不提交**）：写入新生成的 `kob.db.password`、`kob.jwt.secret` 等本地真实值。
- 新增 `application-local.example.properties`（**提交，模板**）：展示需配置的键。
- `.gitignore`：追加 `application-local.properties`；并将 `.gitignore` 本身纳入本次提交（保护性基础设施）。

### 文档
- 新增 `docs/architecture/secret-rotation-runbook.md`：MySQL `ALTER USER 'root'@'localhost' IDENTIFIED BY '...';` 轮换 SQL、`openssl` 生成新 secret、生产环境变量清单、旧 Token 失效说明、回滚指引。

## 5. 验证
- `mvn -pl backend test`：含 `JwtUtilTest`（2）+ `RegisterServiceImplTest`（10）共 12 个用例全绿。
- `mvn -pl backend -am compile`：三模块编译通过。
- **未验证项**：真实 DB 连接（环境无 MySQL，无法 `spring-boot:run` 连库）；生产部署配置未知（审计第 8 节待补）。由 runbook 指导用户完成本地 MySQL 轮换后自测。

## 6. 提交约定
- 在 master 上**单次提交**（任务 1.1 为一个内聚关注点；拆分会产生中间态破坏：JwtUtil 已读外部密钥而配置未就绪）。
- 中文提交信息，正文按审计 7.1 模板。
- 提交前征求确认。pathspec 精确提交，不带入 AGENTS/CLAUDE（staged）与审计文档/.gitignore 历史。

## 7. 回滚
- `git revert <commit>` 整体回退；注意回退后源码恢复旧泄漏密钥，**不应再以此旧密钥运行**（需重新轮换）。

## 8. 风险
- `@Value` 静态 setter 为过渡模式（反模式但最小改动）；后续任务可重构为实例注入。
- 若生产未设置 `KOB_DB_PASSWORD`/`KOB_JWT_SECRET` 环境变量，应用启动后 DB 连接失败、JWT 签发失败--属安全失败（强制配置），runbook 明确说明。
- DB 密码轮换需用户在 MySQL 执行 SQL；轮换前本地 `spring-boot:run` 连库会失败（环境本就无 MySQL，不构成新回归）。
