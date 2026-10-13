-- Run once after confirming that existing account values are unique.
-- Existing NULL accounts remain allowed by MySQL's UNIQUE index rules.
ALTER TABLE demo.`user` ADD UNIQUE KEY ux_user_account (account);
