-- 迁移：用户级真实模型开关（管理员在后台「用户管理」按用户控制能否使用真实大模型）
-- 适用：已按旧版 schema.sql 建库的存量环境（新环境直接导入 schema.sql + seed.sql，无需本脚本）
-- 幂等：可重复执行，在已含该列的库上为空操作
-- 回退（会丢失各用户的开关状态，谨慎执行）：
-- ALTER TABLE public."user" DROP COLUMN IF EXISTS ai_real_enabled;

ALTER TABLE public."user" ADD COLUMN IF NOT EXISTS ai_real_enabled smallint NOT NULL DEFAULT 1;

COMMENT ON COLUMN public."user".ai_real_enabled IS '真实模型开关 1:允许（全局 Key 对其生效） 0:强制演示模式';
