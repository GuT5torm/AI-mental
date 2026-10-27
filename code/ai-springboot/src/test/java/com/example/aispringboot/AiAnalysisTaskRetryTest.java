package com.example.aispringboot;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aispringboot.ai.AiChatClient;
import com.example.aispringboot.entity.AiAnalysisTask;
import com.example.aispringboot.entity.EmotionDiary;
import com.example.aispringboot.mapper.AiAnalysisTaskMapper;
import com.example.aispringboot.mapper.EmotionDiaryMapper;
import com.example.aispringboot.service.AiAnalysisTaskService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * AI 分析任务的重试与失败记账。
 *
 * <p>单独开一个类的原因：这里要用 mock 把大模型替换成必定抛异常的实现，
 * 而 {@link EmotionDiaryFlowTest} 需要真实的 MockAiChatClient 来断言回填内容。
 * 两者的 Spring 上下文不同，混在一起会互相干扰。
 *
 * <p>重试路径无法用"删掉日记制造孤儿任务"来触发——ai_analysis_task 上的
 * fk_ai_task_diary 是 ON DELETE CASCADE，日记一删任务就没了。
 * 真实场景下唯一能走到重试的就是模型调用失败，用例正是这么构造的。
 */
@SpringBootTest
@TestPropertySource(properties = "app.ai.scheduler.enabled=false")
public class AiAnalysisTaskRetryTest {

    @Resource
    private EmotionDiaryMapper diaryMapper;
    @Resource
    private AiAnalysisTaskMapper taskMapper;
    @Resource
    private AiAnalysisTaskService taskService;
    @Resource
    private com.example.aispringboot.service.EmotionDiaryService diaryService;

    /** 替换掉 MockAiChatClient，让每次分析都抛异常 */
    @MockitoBean
    private AiChatClient aiChatClient;

    /** 手动提交接口已移除，直接走 AI 咨询自动记录链路造一条日记 + 任务 */
    private long submitDiary() {
        return diaryService.createDiaryFromChat(2L, 999044L, "最近压力很大", List.of("压力大"), 3);
    }

    private AiAnalysisTask taskOf(long diaryId) {
        List<AiAnalysisTask> tasks = taskMapper.selectList(
                Wrappers.lambdaQuery(AiAnalysisTask.class).eq(AiAnalysisTask::getDiaryId, diaryId));
        assertEquals(1, tasks.size(), "一条日记只应有一条自动分析任务");
        return tasks.get(0);
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 模型调用失败_累加重试次数直到上限才置FAILED() throws Exception {
        when(aiChatClient.analyzeEmotion(anyList(), any()))
                .thenThrow(new IllegalStateException("模型调用超时"));

        long diaryId = submitDiary();

        taskService.processPendingTasks();
        AiAnalysisTask task = taskOf(diaryId);
        assertEquals("PENDING", task.getStatus(), "第一次失败未到上限，应退回 PENDING 等下一轮");
        assertEquals(1, task.getRetryCount());
        assertNotNull(task.getErrorMessage());
        assertNull(diaryMapper.selectById(diaryId).getAiEmotionAnalysis(), "失败不应回填分析结果");

        taskService.processPendingTasks();
        task = taskOf(diaryId);
        assertEquals("PENDING", task.getStatus());
        assertEquals(2, task.getRetryCount());

        // 第三次重试后 retryCount 达到 maxRetryCount=3，判死
        taskService.processPendingTasks();
        task = taskOf(diaryId);
        assertEquals("FAILED", task.getStatus());
        assertEquals(3, task.getRetryCount());
        assertNotNull(task.getCompletedAt(), "判死时补上结束时间，与 COMPLETED 的口径一致");
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 失败后恢复_同一条任务可以重新消费成功() throws Exception {
        when(aiChatClient.analyzeEmotion(anyList(), any()))
                .thenThrow(new IllegalStateException("模型限流"))
                .thenReturn(Map.of(
                        "primaryEmotion", "平静",
                        "emotionScore", 60,
                        "isNegative", false,
                        "suggestion", "保持现在的节奏",
                        "improvementSuggestions", List.of("保持规律作息")));

        long diaryId = submitDiary();

        taskService.processPendingTasks();
        assertEquals(1, taskOf(diaryId).getRetryCount());

        taskService.processPendingTasks();
        AiAnalysisTask task = taskOf(diaryId);
        assertEquals("COMPLETED", task.getStatus(), "模型恢复后应正常完成");
        assertEquals(1, task.getRetryCount(), "重试次数是累计值，成功也不清零");
        assertNull(task.getErrorMessage(), "成功后清掉历史错误信息");

        EmotionDiary diary = diaryMapper.selectById(diaryId);
        assertNotNull(diary.getAiEmotionAnalysis());
        assertNotNull(diary.getAiAnalysisUpdatedAt());
        assertEquals(6, diary.getMoodScore(), "emotionScore=60 应回写心情分 6，覆盖初始分 3");
    }

    @Test
    public void emotionScore换算_十等分并夹取到1到10() {
        assertEquals(10, AiAnalysisTaskService.toMoodScore(100).intValue());
        assertEquals(6, AiAnalysisTaskService.toMoodScore(60).intValue());
        assertEquals(6, AiAnalysisTaskService.toMoodScore(55).intValue(), "四舍五入：5.5 取 6");
        assertEquals(1, AiAnalysisTaskService.toMoodScore(0).intValue());
        assertEquals(1, AiAnalysisTaskService.toMoodScore(7).intValue(), "0.7 四舍五入为 1");
        assertNull(AiAnalysisTaskService.toMoodScore(null), "字段缺失不回写");
        assertNull(AiAnalysisTaskService.toMoodScore("60"), "非数值不回写");
    }
}
