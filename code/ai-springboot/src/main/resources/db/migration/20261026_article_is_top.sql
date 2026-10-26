-- 知识文章置顶：is_top 1=置顶 0=普通
-- 回退：ALTER TABLE knowledge_article DROP COLUMN IF EXISTS is_top;
ALTER TABLE knowledge_article ADD COLUMN IF NOT EXISTS is_top INT NOT NULL DEFAULT 0;
