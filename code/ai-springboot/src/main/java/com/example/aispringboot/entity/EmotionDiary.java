package com.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 情绪日记。
 *
 * <p>aiEmotionAnalysis 在库中为 text，存放 AI 分析结果的 JSON 字符串，
 * 由 ai_analysis_task 异步任务回填。
 */
@Data
@TableName("emotion_diary")
public class EmotionDiary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate diaryDate;

    private Integer moodScore;

    private String dominantEmotion;

    private String emotionTriggers;

    private String diaryContent;

    private Integer sleepQuality;

    private Integer stressLevel;

    private String aiEmotionAnalysis;

    private LocalDateTime aiAnalysisUpdatedAt;

    /** 记录来源：CHAT=AI咨询对话自动记录，MANUAL=手动填写，NULL视为MANUAL */
    private String source;

    /** CHAT 来源时关联的咨询会话ID */
    private Long sourceSessionId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
