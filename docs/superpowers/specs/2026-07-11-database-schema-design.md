# KOB 数据库 Schema 设计

## 目标

为当前空的 `kob` 数据库补齐现有后端运行所需的 `user`、`bot`、`record` 三张表，使注册、登录、Bot 管理、排行榜、对局结算和录像查询能够按现有 Java 代码工作。

本次只恢复现有代码隐含的数据契约，不调整业务模型，不引入数据库迁移框架，不写入演示数据。

## 依据

- `User`、`Bot`、`Record` 实体决定字段集合和 Java 类型。
- MyBatis-Plus 默认驼峰映射决定 `userId` 对应 `user_id`、`aSteps` 对应 `a_steps` 等列名。
- 注册和 Bot Service 的输入校验决定字符串长度上限。
- `GameResultServiceImpl` 的同事务积分更新和录像写入决定表引擎必须支持事务。
- 游戏逻辑决定 `record.loser` 只会写入 `A`、`B`、`all`。

## 交付形式

新增 `backendcloud/database/schema.sql`，由开发者对目标数据库显式执行。脚本包含：

1. `CREATE DATABASE IF NOT EXISTS kob`，字符集为 `utf8mb4`。
2. 按依赖顺序创建 `user`、`bot`、`record`。
3. 使用 `CREATE TABLE IF NOT EXISTS`，允许对已初始化的同构数据库重复执行。
4. 不配置 Spring Boot 启动时自动建表，避免误操作非本地数据库。

## 表结构

### `user`

| 列 | 类型 | 约束 | 代码依据 |
| --- | --- | --- | --- |
| `id` | `INT` | 自增主键 | `IdType.AUTO`、实体 `Integer` |
| `username` | `VARCHAR(100)` | 非空、唯一 | 注册校验最大长度 100，按用户名登录 |
| `password` | `VARCHAR(100)` | 非空 | 注册校验和 BCrypt 编码结果 |
| `photo` | `VARCHAR(1000)` | 非空 | 用户头像 URL |
| `rating` | `INT` | 非空、默认 1500 | 注册默认分和排行榜排序 |

### `bot`

| 列 | 类型 | 约束 | 代码依据 |
| --- | --- | --- | --- |
| `id` | `INT` | 自增主键 | `IdType.AUTO` |
| `user_id` | `INT` | 非空、索引、关联 `user.id` | 按用户查询和所有权校验 |
| `title` | `VARCHAR(100)` | 非空 | Service 最大长度 100 |
| `description` | `VARCHAR(300)` | 非空 | Service 最大长度 300 |
| `content` | `TEXT` | 非空 | Bot 代码最大 10000 字符 |
| `createtime` | `DATETIME` | 非空 | 实体和创建逻辑 |
| `modifytime` | `DATETIME` | 非空 | 实体和更新逻辑 |

### `record`

| 列 | 类型 | 约束 | 代码依据 |
| --- | --- | --- | --- |
| `id` | `INT` | 自增主键 | `IdType.AUTO`、录像倒序分页 |
| `a_id`、`b_id` | `INT` | 非空、索引、关联 `user.id` | 获取双方用户信息 |
| `a_sx`、`a_sy`、`b_sx`、`b_sy` | `INT` | 非空 | 对局出生坐标 |
| `a_steps`、`b_steps` | `TEXT` | 非空 | 完整移动序列 |
| `map` | `TEXT` | 非空 | 序列化地图 |
| `loser` | `VARCHAR(3)` | 非空 | `A`、`B`、`all` |
| `createtime` | `DATETIME` | 非空、索引 | 对局创建时间 |

## 约束策略

- 全部表使用 InnoDB 和 `utf8mb4`。
- 用户名唯一约束作为注册层重复检查之外的并发保护。
- `bot.user_id`、`record.a_id`、`record.b_id` 使用外键 `RESTRICT`，不级联删除 Bot 或历史战绩。
- 不添加数据库 `CHECK` 约束。项目使用 MySQL 8，但保持与旧环境的兼容性，合法值继续由现有业务代码保证。
- 不添加自动更新时间表达式，保留现有 Service 对 `createtime` 和 `modifytime` 的控制。

## 验证

1. 在空的 `kob` 数据库执行脚本，确认无 SQL 错误。
2. 再执行一次，确认建表脚本可重复执行。
3. 查询 `information_schema`，核对表、列、主键、唯一索引、普通索引和外键。
4. 使用 JDK 8 启动 backend，确认 HikariCP 成功连接且无缺表错误。
5. 通过 HTTP 完成注册和登录，验证 `user` 写入及 BCrypt 密码认证。
6. 运行后端测试，确认既有行为未回归。

## 非目标

- 不恢复未知的历史数据。
- 不创建默认用户或默认 Bot。
- 不引入 Flyway、Liquibase 或新的 Maven 依赖。
- 不修改 Java 实体、Mapper 或业务逻辑。
