package com.example.aispringboot.DTO.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 情绪日记响应。
 *
 * <p>后台列表页与详情弹窗用的是同一份数据：emotional.vue 的 viewSessionDetail 直接把
 * 列表行塞进 currentDetail，所以 username / nickname 必须随行返回，不能只在详情接口里补。
 *
 * <p>aiEmotionAnalysis 保持字符串。前端是 {@code JSON.parse(row.aiEmotionAnalysis)}，
 * 若后端直接返回对象，JSON.parse 会抛 SyntaxError 且整个弹窗渲染失败。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class EmotionDiaryResponseDTO {

    private Long id;

    private Long userId;

    /** 用户名，关联 user.username */
    private String username;

    /** 昵称，可能为空 */
    private String nickname;

    private LocalDate diaryDate;

    private Integer moodScore;

    private String dominantEmotion;

    private String emotionTriggers;

    private String diaryContent;

    private Integer sleepQuality;

    private Integer stressLevel;

    /** AI 分析结果的 JSON 字符串，未分析时为 null */
    private String aiEmotionAnalysis;

    /** 记录来源：CHAT=AI咨询对话自动记录，MANUAL=手动填写，NULL视为MANUAL */
    private String source;

    private LocalDateTime aiAnalysisUpdatedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
