-- 迁移：情绪触发关键词引入等级（level），支撑自动记录时的初始心情分推导
-- 等级语义（mood_score 1-10，分越低心情越差）：
--   1=轻度（日常吐槽信号）  2=中度（明确负向状态）  3=重度（痛苦/危机信号）
-- 初始分推导规则：取命中词中最重等级定档（1→4 分、2→3 分、3→2 分），
-- 同一等级命中 ≥3 个不同词再降 1 分，最低 1 分；未命中走默认分。
-- 适用：已按旧版结构导入关键词的存量环境（新环境直接导入 schema.sql + seed.sql）
-- 幂等：可重复执行

ALTER TABLE public.emotion_keyword
    ADD COLUMN IF NOT EXISTS level smallint NOT NULL DEFAULT 1;

COMMENT ON COLUMN public.emotion_keyword.level IS '词的严重度等级：1=轻度 2=中度 3=重度，用于自动记录时推导初始心情分';

-- 存量 43 词回填等级
UPDATE public.emotion_keyword SET level = 3 WHERE keyword IN ('崩溃', '绝望', '撑不住', '喘不过气', '抑郁');
UPDATE public.emotion_keyword SET level = 2 WHERE keyword IN (
    '焦虑', '难受', '压力大', '失眠', '想哭', '心累', '烦躁', '难过', '伤心', '委屈',
    '睡不着', '无助', '疲惫', '郁闷', '心情不好', '心情糟糕', '糟糕', '心情很差',
    '低落', '沮丧', '空虚', '压抑'
);
UPDATE public.emotion_keyword SET level = 1 WHERE keyword IN (
    '不开心', '不高兴', '心烦', '好烦', '烦死了', '好累', '太累', '累死了',
    'emo', '迷茫', '压力', '孤独', '心慌', '睡不好', '没睡好', '失落'
);

-- 继续扩充口语词库（共 15 词），与等级一同落库
INSERT INTO public.emotion_keyword (keyword, enabled, level)
SELECT v.kw, 1, v.lv FROM (VALUES
    ('不想活', 3), ('活着没意思', 3), ('想消失', 3), ('撑不下去', 3),
    ('自责', 2), ('内疚', 2), ('恐慌', 2), ('破防', 2), ('泪崩', 2), ('心力交瘁', 2),
    ('没劲', 1), ('无聊', 1), ('心塞', 1), ('不想动', 1), ('提不起劲', 1)
) AS v(kw, lv)
WHERE NOT EXISTS (SELECT 1 FROM public.emotion_keyword e WHERE e.keyword = v.kw);

-- 回退：
-- ALTER TABLE public.emotion_keyword DROP COLUMN IF EXISTS level;
-- DELETE FROM public.emotion_keyword WHERE keyword IN (
--     '不想活','活着没意思','想消失','撑不下去',
--     '自责','内疚','恐慌','破防','泪崩','心力交瘁',
--     '没劲','无聊','心塞','不想动','提不起劲'
-- );
