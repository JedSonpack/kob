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
