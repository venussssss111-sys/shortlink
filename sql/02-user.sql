-- 用户表（第 5 周：登录鉴权）
-- 用法：mysql -h127.0.0.1 -P3307 -uroot -p123456 shortlink < sql/02-user.sql

CREATE TABLE IF NOT EXISTS `user`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `username`   VARCHAR(32)     NOT NULL COMMENT '登录名，唯一',
    `password`   VARCHAR(64)     NOT NULL COMMENT 'MD5(salt + 明文)，60 位以内',
    `salt`       VARCHAR(32)     NOT NULL COMMENT '每个用户独立随机盐',
    `nickname`   VARCHAR(32)     NULL,
    `avatar`     VARCHAR(255)    NULL COMMENT '头像地址，如 /uploads/avatar/20261028_xxx.png',
    `email`      VARCHAR(64)     NULL,
    `is_admin`   TINYINT         NOT NULL DEFAULT 0 COMMENT '1=管理员',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_bin COMMENT ='管理台用户';

-- 默认账号：admin / 123456（salt 固定，方便演示；真实系统不要内置账号）
INSERT INTO `user` (`username`, `password`, `salt`, `nickname`, `email`, `is_admin`)
VALUES ('admin', '3afccc2bb4341b0930f5c3fb14e3859a', '0a1b2c3d4e5f60718293a4b5c6d7e8f9', '管理员', 'admin@example.com', 1)
ON DUPLICATE KEY UPDATE `nickname` = VALUES(`nickname`), `is_admin` = 1;
