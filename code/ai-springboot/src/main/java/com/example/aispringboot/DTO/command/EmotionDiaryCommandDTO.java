package com.example.aispringboot.DTO.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 情绪日记提交参数。
 *
 * <p>字段名与前端 emotionDiary.vue 的 diaryForm 一致。除 moodScore 外全部可空：
 * 页面上的主要情绪、触发因素、正文、睡眠质量、压力水平都是选填项，
 * 只有情绪评分在提交前做了非空校验。
 */
@Data
public class EmotionDiaryCommandDTO {

    /** 记录日期，前端默认传当天；后端在未传时也兜底为今天 */
    private LocalDate diaryDate;

    /** 情绪评分，1–10，对应前端 el-rate 的 max=10 */
    @NotNull(message = "请选择情绪评分")
    @Min(value = 1, message = "情绪评分应在 1 到 10 之间")
    @Max(value = 10, message = "情绪评分应在 1 到 10 之间")
    private Integer moodScore;

    private String dominantEmotion;

    private String emotionTriggers;

    private String diaryContent;

    /** 睡眠质量，1–5 */
    @Min(value = 1, message = "睡眠质量应在 1 到 5 之间")
    @Max(value = 5, message = "睡眠质量应在 1 到 5 之间")
    private Integer sleepQuality;

    /** 压力水平，1–5 */
    @Min(value = 1, message = "压力水平应在 1 到 5 之间")
    @Max(value = 5, message = "压力水平应在 1 到 5 之间")
    private Integer stressLevel;
}
