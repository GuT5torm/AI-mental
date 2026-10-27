package com.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 情绪触发关键词。
 *
 * <p>AI 咨询对话中用户消息命中 keyword（enabled=1）时，
 * 自动记录一条来源为 CHAT 的情绪记录并投递 AI 评估任务。
 */
@Data
@TableName("emotion_keyword")
public class EmotionKeyword {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String keyword;

    /** 1=启用，0=停用 */
    private Integer enabled;

    /**
     * 词的严重度等级：1=轻度（日常吐槽信号）、2=中度（明确负向状态）、
     * 3=重度（痛苦/危机信号）。自动记录情绪日志时按命中词推导初始心情分。
     */
    private Integer level;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
