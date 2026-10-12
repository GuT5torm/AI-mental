package com.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 分析任务队列。
 *
 * <p>taskType 实测两种取值：AUTO（用户提交日记时自动创建）、ADMIN（管理端手动触发）。
 * status 实测为 COMPLETED，按设计另有待处理与失败态，由阶段 4 的任务消费者维护。
 */
@Data
@TableName("ai_analysis_task")
public class AiAnalysisTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long diaryId;

    private Long userId;

    private String status;

    private String taskType;

    private Integer priority;

    private Integer retryCount;

    private Integer maxRetryCount;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
