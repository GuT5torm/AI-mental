package com.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 心理咨询会话。
 *
 * <p>lastEmotionAnalysis 在库中为 jsonb，此处按原始 JSON 字符串读取，
 * 结构化解析在阶段 3 的会话情绪接口中完成。
 */
@Data
@TableName("consultation_session")
public class ConsultationSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionTitle;

    private LocalDateTime startedAt;

    private String lastEmotionAnalysis;

    private LocalDateTime lastEmotionUpdatedAt;

}
