-- ============================================================
-- v7：短链接归属（多租户隔离）
--
-- 背景：v1~v6 只有「登录」这一个权限概念，登录了就等于拥有全部数据：
--       A 用户能停用、删除 B 用户的短链。这是越权（IDOR）。
--
-- 这个脚本做三件事：
--   1. short_link 加 user_id（谁创建的）
--   2. user 加 is_admin（管理员可跨用户查看）
--   3. 历史数据（匿名时代建的）全部归给 admin
--
-- ⚠️ 只执行一次：MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS
--    在容器里执行：
--    docker exec -i shortlink-mysql mysql -uroot -p123456 shortlink < sql/03-multi-tenant.sql
-- ============================================================

ALTER TABLE `short_link`
    ADD COLUMN `user_id` BIGINT UNSIGNED NULL COMMENT '创建者 user.id，NULL=匿名时代的历史数据' AFTER `id`,
    ADD KEY `idx_user_id` (`user_id`);

ALTER TABLE `user`
    ADD COLUMN `is_admin` TINYINT NOT NULL DEFAULT 0 COMMENT '1=管理员，可跨用户查看' AFTER `email`;

-- 存量短链没有主人，统一归给 admin
UPDATE `short_link` SET `user_id` = 1 WHERE `user_id` IS NULL;

-- admin 设为管理员
UPDATE `user` SET `is_admin` = 1 WHERE `username` = 'admin';
