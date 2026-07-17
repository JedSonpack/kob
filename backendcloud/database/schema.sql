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

CREATE TABLE IF NOT EXISTS `agent_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `goal` VARCHAR(1000) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `current_iteration` TINYINT NOT NULL DEFAULT 0,
  `max_iterations` TINYINT NOT NULL,
  `best_version_id` BIGINT NULL,
  `active_slot` TINYINT NULL,
  `version` INT NOT NULL DEFAULT 0,
  `error_code` VARCHAR(64) NULL,
  `error_message` VARCHAR(1000) NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_task_user_active` (`user_id`, `active_slot`),
  KEY `idx_agent_task_status` (`status`),
  CONSTRAINT `fk_agent_task_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bot_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `iteration` TINYINT NOT NULL,
  `attempt` TINYINT NOT NULL,
  `parent_version_id` BIGINT NULL,
  `source_code` MEDIUMTEXT NOT NULL,
  `strategy_summary` VARCHAR(1000) NOT NULL,
  `change_reason` VARCHAR(1000) NULL,
  `compile_status` VARCHAR(32) NOT NULL,
  `compile_error` TEXT NULL,
  `accepted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bot_version_attempt` (`task_id`, `iteration`, `attempt`),
  KEY `idx_bot_version_parent` (`parent_version_id`),
  CONSTRAINT `fk_bot_version_task`
    FOREIGN KEY (`task_id`) REFERENCES `agent_task` (`id`)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `agent_step` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `sequence` INT NOT NULL,
  `phase` VARCHAR(32) NOT NULL,
  `tool_name` VARCHAR(64) NOT NULL,
  `idempotency_key` VARCHAR(160) NOT NULL,
  `input_summary` TEXT NULL,
  `output_summary` TEXT NULL,
  `status` VARCHAR(32) NOT NULL,
  `duration_ms` BIGINT NULL,
  `prompt_tokens` INT NULL,
  `completion_tokens` INT NULL,
  `error_code` VARCHAR(64) NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_step_idempotency` (`idempotency_key`),
  UNIQUE KEY `uk_agent_step_sequence` (`task_id`, `sequence`),
  CONSTRAINT `fk_agent_step_task`
    FOREIGN KEY (`task_id`) REFERENCES `agent_task` (`id`)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `evaluation_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `version_id` BIGINT NOT NULL,
  `dataset_type` VARCHAR(16) NOT NULL,
  `opponent_key` VARCHAR(32) NOT NULL,
  `map_seed` BIGINT NOT NULL,
  `side` CHAR(1) NOT NULL,
  `result` VARCHAR(16) NOT NULL,
  `rounds` INT NOT NULL,
  `decision_p95_ms` BIGINT NOT NULL,
  `invalid_move_count` INT NOT NULL,
  `failure_reason` VARCHAR(64) NULL,
  `replay` MEDIUMTEXT NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evaluation_match`
    (`version_id`, `dataset_type`, `opponent_key`, `map_seed`, `side`),
  KEY `idx_evaluation_version_dataset` (`version_id`, `dataset_type`),
  CONSTRAINT `fk_evaluation_version`
    FOREIGN KEY (`version_id`) REFERENCES `bot_version` (`id`)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
