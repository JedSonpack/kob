# 密钥轮换操作手册

> 配套：`docs/architecture/refactor-plan-config-externalization.md`（审计任务 1.1）。
> 适用：本地开发与生产部署的 DB 密码、JWT 密钥轮换。
> 创建日期：2026-07-11

## 1. 背景

历史仓库曾硬编码：
- DB 密码 `123456`（`root@localhost`）。
- JWT 密钥 `SDFGjhdsfalshdfHFdsjkdsfds121232131afasdfac`。

两者已进入 Git 历史。任务 1.1 将其移出源码并轮换；本手册指导完成本地/生产的实际轮换。

## 2. 配置加载机制

| 文件/来源 | 是否提交 | 作用 |
| --- | --- | --- |
| `application.properties` | 是 | 占位符与默认值，零真实密钥；dev 默认 `spring.profiles.active=local` |
| `application-local.properties` | 否（gitignore） | 本地真实密钥 |
| `application-local.example.properties` | 是 | 模板 |
| 环境变量 `KOB_DB_PASSWORD` / `KOB_JWT_SECRET` 等 | — | 生产覆盖 |

`JwtUtil#validate` 在启动时校验密钥非空，未配置则启动失败（安全失败，非静默用空密钥）。

## 3. 本地开发轮换

1. 生成新密钥：
   ```bash
   openssl rand -base64 48   # JWT 密钥
   openssl rand -base64 18   # DB 密码
   ```
2. 写入 `backendcloud/backend/src/main/resources/application-local.properties`（已 gitignore）：
   ```properties
   kob.db.password=<新DB密码>
   kob.jwt.secret=<新JWT密钥>
   ```
3. 在 MySQL 中把 root 密码改成新值（使配置与数据库匹配）：
   ```sql
   ALTER USER 'root'@'localhost' IDENTIFIED BY '<新DB密码>';
   FLUSH PRIVILEGES;
   ```
4. 启动 backend 验证：`mvn -pl backend spring-boot:run`，确认连库成功、登录签发 Token 正常。
5. 旧 Token 全部失效（密钥已变），用户需重新登录。

## 4. 生产部署轮换

1. 设置环境变量（勿写入镜像/仓库）：
   ```bash
   export SPRING_PROFILES_ACTIVE=prod
   export KOB_DB_URL=jdbc:mysql://<host>:3306/kob?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8
   export KOB_DB_USERNAME=<用户>
   export KOB_DB_PASSWORD=<强密码>
   export KOB_JWT_SECRET=<openssl rand -base64 48>
   ```
2. 重启服务，确认启动日志无 `kob.jwt.secret 未配置`，连库与登录正常。
3. 旧 Token 失效，用户需重新登录。

## 5. 历史密钥处理

- 旧 DB 密码 `123456` 与旧 JWT 密钥已从源码移除，但因仍在 Git 历史中，**不能仅靠移除**：
  - DB：已在第 3/4 步轮换为新密码，旧密码不再有效。
  - JWT：新密钥签发的 Token 才被接受，旧密钥 Token 自动失效。
- 如需彻底清除历史中的密钥（更彻底），需用 `git filter-repo` 重写历史并强推，代价较大，建议评估后另行处理，不在本任务范围。

## 6. 回滚

- 代码回滚：`git revert <任务 1.1 提交>`。注意回滚后源码恢复旧泄漏密钥，**不应再以旧密钥运行**，需重新轮换。
- 配置回滚：恢复上一版 `application-local.properties`（本地）或环境变量（生产）。

## 7. 验证与未验证项

本地验证（2026-07-11，MySQL 8.0.26）：
- DB 连接：backend HikariCP 用 `application-local.properties` 中的轮换密码连上 MySQL，查询触达 `kob.user`（空库报 `Table 'kob.user' doesn't exist`，属 schema 缺失，非配置问题）。
- JWT 签发/校验：`JwtUtilTest` 通过；启动时 `validate()` 通过（密钥从配置注入，未配置则启动失败已验证机制有效）。
- root 账号：已重置为新密码，插件 `mysql_native_password`。
- 已建空 `kob` 库（utf8mb4），待补 `user`/`bot`/`record` 表 schema。

仍未验证：
- 完整登录链路（需 `user` 表与种子数据；审计列为独立 gap：仓库无建表脚本）。
- 生产部署配置（Nginx/Docker/AcWing）未知（审计第 8 节待补），第 4 节为通用指引。
