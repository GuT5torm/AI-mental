package com.example.aispringboot.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.example.aispringboot.DTO.response.SessionMessageStatDTO;
import com.example.aispringboot.entity.ConsultationMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 咨询消息。
 *
 * <p>会话列表需要 lastMessageContent / messageCount / durationMinutes 三个派生字段，
 * 库里没有对应列。这里用一次 GROUP BY 聚合拿到计数与首末消息时间，
 * 再由 Service 按 last_message_id 批量取最后一条正文；
 * 不写成子查询，是为了避开 jsqlparser 对复杂 SQL 的解析风险
 * （分页插件依赖它，3.5.17 起才独立成模块）。
 */
public interface ConsultationMessageMapper extends BaseMapper<ConsultationMessage> {

    /**
     * 按会话分组统计消息。
     *
     * <p>调用方需传入含 {@code in("session_id", ids)} 与 {@code groupBy("session_id")} 的 wrapper。
     * 别名用下划线写法，配合 map-underscore-to-camel-case 映射到 DTO 字段。
     */
    @Select("""
            SELECT session_id,
                   count(*)          AS message_count,
                   min(created_at)   AS first_message_at,
                   max(created_at)   AS last_message_at,
                   max(id)           AS last_message_id
              FROM consultation_message
            ${ew.customSqlSegment}
            """)
    List<SessionMessageStatDTO> selectStatsBySession(@Param(Constants.WRAPPER) Wrapper<ConsultationMessage> wrapper);

    @Delete("DELETE FROM consultation_message WHERE session_id = #{sessionId}")
    int deleteBySessionId(@Param("sessionId") Long sessionId);
}
