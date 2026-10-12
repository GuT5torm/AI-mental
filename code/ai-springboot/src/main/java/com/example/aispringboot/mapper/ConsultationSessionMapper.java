package com.example.aispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aispringboot.entity.ConsultationSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 咨询会话。
 *
 * <p>{@code last_emotion_analysis} 在库中是 jsonb。PostgreSQL 不允许把 varchar
 * 参数隐式写入 jsonb 列，而 postgresql 驱动是 runtime scope、主代码拿不到
 * {@code PGobject}，所以这里用 {@code #{json}::jsonb} 显式转换，
 * 已用 psql 的 PREPARE 验证过该写法可用。
 */
public interface ConsultationSessionMapper extends BaseMapper<ConsultationSession> {

    /**
     * 回写会话的情绪分析结果。
     *
     * <p>不走 BaseMapper.updateById，是因为那会拼出
     * {@code last_emotion_analysis = ?}（varchar），落到 jsonb 列上报类型不匹配。
     */
    @Update("""
            UPDATE consultation_session
               SET last_emotion_analysis = #{json}::jsonb,
                   last_emotion_updated_at = #{updatedAt}
             WHERE id = #{id}
            """)
    int updateEmotionAnalysis(@Param("id") Long id,
                              @Param("json") String json,
                              @Param("updatedAt") LocalDateTime updatedAt);
}
