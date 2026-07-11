# KOB 数据库 Schema 实施计划

> **面向智能体工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐项实施此计划。步骤使用复选框（`- [ ]`）语法跟踪。

**目标：** 创建并应用与现有 Java 数据模型完全匹配的 MySQL schema，使 KOB 后端的全部数据库链路可运行。

**架构：** 使用一份显式执行、可重复建表的 SQL 文件管理初始 schema。Spring Boot 不自动建表；数据库结构通过 MySQL 元数据断言、后端测试和 HTTP 注册登录链路验证。

**技术栈：** MySQL 8.0、InnoDB、utf8mb4、Spring Boot 2.4.5、MyBatis-Plus 3.5.1、JDK 8

## 全局约束

- 只创建现有后端需要的 `user`、`bot`、`record` 三张表。
- 不引入 Flyway、Liquibase 或新的 Maven 依赖。
- 不修改 Java 实体、Mapper 或业务逻辑。
- 不写入默认账号、默认 Bot 或演示数据。
- 不配置 Spring Boot 启动时自动建表。
- 外键使用 `RESTRICT`，不级联删除历史数据。

---

### 任务 1：初始数据库结构

**文件：**
- 创建：`backendcloud/database/schema.sql`
- 参考：`backendcloud/backend/src/main/java/com/kob/backend/pojo/User.java`
- 参考：`backendcloud/backend/src/main/java/com/kob/backend/pojo/Bot.java`
- 参考：`backendcloud/backend/src/main/java/com/kob/backend/pojo/Record.java`

**接口：**
- 消费：MyBatis-Plus 默认实体名和驼峰列名映射。
- 产出：可由 MySQL CLI 执行的 `backendcloud/database/schema.sql`。

- [x] **步骤 1：确认缺表基线**

运行：

```bash
/usr/local/mysql/bin/mysql -h 127.0.0.1 -P 3306 -u root -p \
  --batch --skip-column-names \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='kob' AND table_name IN ('user','bot','record');"
```

预期：输出 `0`，证明验证能够识别当前缺失的 schema。

- [x] **步骤 2：编写最小建表脚本**

创建 `backendcloud/database/schema.sql`：

```sql
CREATE DATABASE IF NOT EXISTS `kob`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `kob`;

CREATE TABLE IF NOT EXISTS `user` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(100) NOT NULL,
  `password` VARCHAR(100) NOT NULL,
  `photo` VARCHAR(1000) NOT NULL,
  `rating` INT NOT NULL DEFAULT 1500,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bot` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `title` VARCHAR(100) NOT NULL,
  `description` VARCHAR(300) NOT NULL,
  `content` TEXT NOT NULL,
  `createtime` DATETIME NOT NULL,
  `modifytime` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bot_user_id` (`user_id`),
  CONSTRAINT `fk_bot_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `record` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `a_id` INT NOT NULL,
  `a_sx` INT NOT NULL,
  `a_sy` INT NOT NULL,
  `b_id` INT NOT NULL,
  `b_sx` INT NOT NULL,
  `b_sy` INT NOT NULL,
  `a_steps` TEXT NOT NULL,
  `b_steps` TEXT NOT NULL,
  `map` TEXT NOT NULL,
  `loser` VARCHAR(3) NOT NULL,
  `createtime` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_record_a_id` (`a_id`),
  KEY `idx_record_b_id` (`b_id`),
  KEY `idx_record_createtime` (`createtime`),
  CONSTRAINT `fk_record_user_a`
    FOREIGN KEY (`a_id`) REFERENCES `user` (`id`)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT `fk_record_user_b`
    FOREIGN KEY (`b_id`) REFERENCES `user` (`id`)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [x] **步骤 3：应用并验证可重复执行**

连续运行两次：

```bash
/usr/local/mysql/bin/mysql -h 127.0.0.1 -P 3306 -u root -p \
  < backendcloud/database/schema.sql
```

预期：两次命令退出码均为 `0`。

- [x] **步骤 4：核验结构元数据**

运行：

```bash
/usr/local/mysql/bin/mysql -h 127.0.0.1 -P 3306 -u root -p kob \
  -e "SHOW CREATE TABLE user; SHOW CREATE TABLE bot; SHOW CREATE TABLE record;"
```

预期：三张表均为 InnoDB、`utf8mb4`，字段、索引和外键与脚本一致。

- [x] **步骤 5：提交 schema**

```bash
git add backendcloud/database/schema.sql
git commit -m "feat(数据库): 恢复现有业务运行所需的数据契约"
```

提交必须包含 Lore trailers，并注明实际验证结果。

### 任务 2：应用级验证

**文件：**
- 不新增文件。
- 验证：`backendcloud/backend/src/test/java/com/kob/backend/`

**接口：**
- 消费：任务 1 创建的 `kob.user`、`kob.bot`、`kob.record`。
- 产出：后端测试、启动和注册登录链路的验证证据。

- [x] **步骤 1：运行后端测试**

运行：

```bash
cd backendcloud/backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home \
  "/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" test
```

预期：全部测试通过，退出码为 `0`。

- [x] **步骤 2：启动后端并确认数据库连接**

运行：

```bash
cd backendcloud/backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home \
  "/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" \
  clean spring-boot:run
```

预期：日志出现 `Started BackendApplication`，端口 `3000` 开始监听，无缺表或鉴权配置错误。

- [x] **步骤 3：验证注册和登录**

使用唯一的临时用户名调用：

```bash
curl -sS -X POST http://127.0.0.1:3000/api/user/account/register/ \
  -d 'username=schema_verify&password=test123&confirmedPassword=test123'

curl -sS -X POST http://127.0.0.1:3000/api/user/account/token/ \
  -d 'username=schema_verify&password=test123'
```

预期：注册返回 `error_message=success`；登录返回非空 Token。

- [x] **步骤 4：清理验证账号并停止服务**

运行：

```sql
DELETE FROM `user` WHERE `username` = 'schema_verify';
```

向后端进程发送 `SIGTERM`，再确认 `3000` 端口未监听。

- [x] **步骤 5：记录最终状态**

更新 `docs/architecture/refactor-progress.md` 的环境状态：三张业务表已通过版本化脚本创建，注册登录链路已验证；若任一步未通过，准确记录实际缺口，不声明完成。
