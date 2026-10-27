package com.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aispringboot.ai.AiChatClient;
import com.example.aispringboot.ai.ChatMessage;
import com.example.aispringboot.entity.AiAnalysisTask;
import com.example.aispringboot.entity.EmotionDiary;
import com.example.aispringboot.mapper.AiAnalysisTaskMapper;
import com.example.aispringboot.mapper.EmotionDiaryMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 分析任务队列的消费者。
 *
 * <p>提交日记时只写一条 PENDING 任务，由这里轮询处理。这么做的原因有两个：
 * 一是模型调用可能超时，放在请求线程里会把接口拖死；二是 ai_analysis_task 表
 * 本身就带 retry_count / max_retry_count / error_message，结构上就是为重试设计的。
 *
 * <p>状态流转：PENDING → PROCESSING → COMPLETED / FAILED。
 * 失败时累加 retryCount，未到上限就退回 PENDING 等下一轮，到达上限才置 FAILED，
 * 避免一次网络抖动把任务永久判死。
 *
 * <p>轮询在单实例下是安全的：fixedDelay 保证上一轮跑完才会起下一轮，
 * 不会出现两个消费者同时捞到同一批任务。多实例部署时需要换成
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} 或分布式锁。
 */
@Slf4j
@Service
public class AiAnalysisTaskService {

    /** 单轮最多处理的任务数，防止一次积压把线程池占满 */
    private static final int BATCH_SIZE = 20;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    @Resource
    private AiAnalysisTaskMapper taskMapper;

    @Resource
    private EmotionDiaryMapper diaryMapper;

    @Resource
    private UserAiConfigService userAiConfigService;

    @Resource
    private AiChatClient aiChatClient;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 轮询间隔可配置，默认 5 秒。用 fixedDelay 而非 fixedRate，
     * 保证上一轮没跑完就不会起新一轮。
     */
    @Scheduled(initialDelayString = "${app.ai.task-initial-delay:3000}",
            fixedDelayString = "${app.ai.task-poll-delay:5000}")
    public void pollPendingTasks() {
        try {
            processPendingTasks();
        } catch (Exception e) {
            // 轮询本身不能因为单轮失败就停摆，这里吞掉异常等下一轮
            log.error("AI 分析任务轮询异常", e);
        }
    }

    /**
     * 处理一批待处理任务。抽出公开方法是为了让测试可以直接驱动，
     * 不必等定时器触发。
     *
     * @return 本轮处理的任务数
     */
    public int processPendingTasks() {
        List<AiAnalysisTask> pending = claimPending();
        for (AiAnalysisTask task : pending) {
            try {
                processOne(task);
            } catch (Exception e) {
                log.error("处理 AI 分析任务 {} 失败", task.getId(), e);
                markFailure(task, e);
            }
        }
        return pending.size();
    }

    private List<AiAnalysisTask> claimPending() {
        LambdaQueryWrapper<AiAnalysisTask> wrapper = Wrappers.lambdaQuery(AiAnalysisTask.class)
                .eq(AiAnalysisTask::getStatus, STATUS_PENDING)
                .orderByDesc(AiAnalysisTask::getPriority)
                .orderByAsc(AiAnalysisTask::getId)
                .last("LIMIT " + BATCH_SIZE);
        List<AiAnalysisTask> tasks = taskMapper.selectList(wrapper);
        for (AiAnalysisTask task : tasks) {
            taskMapper.update(null, Wrappers.lambdaUpdate(AiAnalysisTask.class)
                    .set(AiAnalysisTask::getStatus, STATUS_PROCESSING)
                    .set(AiAnalysisTask::getStartedAt, LocalDateTime.now())
                    .eq(AiAnalysisTask::getId, task.getId()));
        }
        return tasks;
    }

    /**
     * 处理单个任务。
     *
     * <p>刻意不加 {@code @Transactional}：本方法由同类的 processPendingTasks 调用，
     * 自调用不会经过 Spring 代理，注解加了也不生效，反而让人误以为有事务边界。
     * 两条 update 各自独立提交即可——日记已回填但任务未置 COMPLETED 时，
     * 下一轮最多是重新分析一次，结果一致，没有副作用。
     */
    public void processOne(AiAnalysisTask task) {
        EmotionDiary diary = diaryMapper.selectById(task.getDiaryId());
        if (diary == null) {
            throw new IllegalStateException("关联的日记不存在，diaryId=" + task.getDiaryId());
        }

        // 日记归属用户的自配 Key 优先，其次管理员全局 Key；都没有时走服务端默认实现
        Map<String, Object> emotion = aiChatClient.analyzeEmotion(buildPrompt(diary),
                userAiConfigService.resolveOptionsWithFallback(diary.getUserId()));
        String json = toJson(emotion);

        // 评估完成用 emotionScore(0-100) 换算回写心情分（1-10），覆盖关键词等级推导的初始分
        Integer aiMoodScore = toMoodScore(emotion.get("emotionScore"));

        diaryMapper.update(null, Wrappers.lambdaUpdate(EmotionDiary.class)
                .set(EmotionDiary::getAiEmotionAnalysis, json)
                .set(EmotionDiary::getAiAnalysisUpdatedAt, LocalDateTime.now())
                .set(aiMoodScore != null, EmotionDiary::getMoodScore, aiMoodScore)
                .eq(EmotionDiary::getId, diary.getId()));

        taskMapper.update(null, Wrappers.lambdaUpdate(AiAnalysisTask.class)
                .set(AiAnalysisTask::getStatus, STATUS_COMPLETED)
                .set(AiAnalysisTask::getCompletedAt, LocalDateTime.now())
                .set(AiAnalysisTask::getErrorMessage, null)
                .eq(AiAnalysisTask::getId, task.getId()));

        log.info("AI 分析任务 {} 完成，diaryId={}", task.getId(), task.getDiaryId());
    }

    /**
     * 失败记账。未到重试上限退回 PENDING，下一轮继续；
     * 到达上限置 FAILED 并留下错误信息。
     */
    private void markFailure(AiAnalysisTask task, Exception e) {
        int retried = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        int max = task.getMaxRetryCount() == null ? 3 : task.getMaxRetryCount();
        boolean exhausted = retried >= max;
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();

        try {
            taskMapper.update(null, Wrappers.lambdaUpdate(AiAnalysisTask.class)
                    .set(AiAnalysisTask::getRetryCount, retried)
                    .set(AiAnalysisTask::getStatus, exhausted ? STATUS_FAILED : STATUS_PENDING)
                    .set(AiAnalysisTask::getErrorMessage, message.length() > 500 ? message.substring(0, 500) : message)
                    .set(exhausted, AiAnalysisTask::getCompletedAt, LocalDateTime.now())
                    .eq(AiAnalysisTask::getId, task.getId()));
        } catch (Exception updateEx) {
            log.error("回写任务 {} 失败状态出错", task.getId(), updateEx);
        }
    }

    private String toJson(Map<String, Object> emotion) {
        try {
            return objectMapper.writeValueAsString(emotion);
        } catch (JsonProcessingException e) {
            // 结构化结果序列化失败，等同于分析失败，交给外层走重试
            throw new IllegalStateException("分析结果序列化失败", e);
        }
    }

    /**
     * 模型返回的 emotionScore(0-100，越高越好) 换算成心情分(1-10，越低越差)：
     * 十等分后四舍五入并夹取到 1-10。字段缺失或非法时返回 null，表示不回写、
     * 保留关键词等级推导的初始分。
     */
    public static Integer toMoodScore(Object emotionScore) {
        if (!(emotionScore instanceof Number number)) {
            return null;
        }
        int mood = Math.round(number.floatValue() / 10f);
        return Math.max(1, Math.min(10, mood));
    }
    /**
     * 把一条日记拼成模型输入。
     *
     * <p>只放 user 角色的文本：MockAiChatClient 与多数真实实现都是从 user 轮次里取内容，
     * 且日记本身没有多轮上下文。
     */
    private List<ChatMessage> buildPrompt(EmotionDiary diary) {
        StringBuilder sb = new StringBuilder();
        sb.append("记录日期：").append(diary.getDiaryDate()).append('\n');
        sb.append("情绪评分（1-10）：").append(diary.getMoodScore()).append('\n');
        if (diary.getDominantEmotion() != null) {
            sb.append("主要情绪：").append(diary.getDominantEmotion()).append('\n');
        }
        if (diary.getSleepQuality() != null) {
            sb.append("睡眠质量（1-5）：").append(diary.getSleepQuality()).append('\n');
        }
        if (diary.getStressLevel() != null) {
            sb.append("压力水平（1-5）：").append(diary.getStressLevel()).append('\n');
        }
        if (diary.getEmotionTriggers() != null) {
            sb.append("情绪触发因素：").append(diary.getEmotionTriggers()).append('\n');
        }
        if (diary.getDiaryContent() != null) {
            sb.append("日记正文：").append(diary.getDiaryContent()).append('\n');
        }
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(sb.toString()));
        return messages;
    }
}
