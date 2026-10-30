-- ============================================================
-- 短链接服务建库建表脚本
-- 注意：数据库和 code 字段必须使用 utf8mb4_bin（区分大小写），
--      MySQL 默认的 utf8mb4_0900_ai_ci 会把 aB3 和 Ab3 当成同一个值。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `shortlink`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_bin;

USE `shortlink`;

-- ------------------------------------------------------------
-- 短链主表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `short_link`;
CREATE TABLE `short_link` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT UNSIGNED NULL                    COMMENT '创建者 user.id',
    `code`        VARCHAR(16)     NOT NULL                COMMENT '短码，区分大小写',
    `origin_url`  VARCHAR(2048)   NOT NULL                COMMENT '原始长链接',
    `expire_time` DATETIME        NULL                    COMMENT '过期时间，NULL 表示永久',
    `status`      TINYINT         NOT NULL DEFAULT 1      COMMENT '1 正常，0 停用',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_expire_time` (`expire_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin COMMENT ='短链主表';

-- ------------------------------------------------------------
-- 访问原始日志（v3 使用，量最大，后续按天归档）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `access_log`;
CREATE TABLE `access_log` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `code`        VARCHAR(16)     NOT NULL,
    `access_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `ip`          VARCHAR(45)     NULL COMMENT 'IPv6 最长 45 字符',
    `user_agent`  VARCHAR(500)    NULL,
    `referer`     VARCHAR(500)    NULL COMMENT '来源页，用于来源分布统计',
    PRIMARY KEY (`id`),
    KEY `idx_code_time` (`code`, `access_time`),
    KEY `idx_access_time` (`access_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin COMMENT ='访问原始日志';

-- ------------------------------------------------------------
-- 按天汇总表（看板只查这张，别去扫 access_log）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `link_stats`;
CREATE TABLE `link_stats` (
    `id`        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `code`      VARCHAR(16)     NOT NULL,
    `stat_date` DATE            NOT NULL,
    `pv`        BIGINT          NOT NULL DEFAULT 0 COMMENT '访问次数',
    `uv`        BIGINT          NOT NULL DEFAULT 0 COMMENT '独立访客数（HyperLogLog 估算）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code_date` (`code`, `stat_date`),
    KEY `idx_stat_date` (`stat_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin COMMENT ='短链日汇总统计';
