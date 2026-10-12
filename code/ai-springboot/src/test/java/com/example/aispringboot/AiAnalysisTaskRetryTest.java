package com.example.aispringboot;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aispringboot.ai.AiChatClient;
import com.example.aispringboot.common.JwtUtil;
import com.example.aispringboot.entity.AiAnalysisTask;
import com.example.aispringboot.entity.EmotionDiary;
import com.example.aispringboot.mapper.AiAnalysisTaskMapper;
import com.example.aispringboot.mapper.EmotionDiaryMapper;
import com.example.aispringboot.service.AiAnalysisTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.scheduler.enabled=false")
public class AiAnalysisTaskRetryTest {

    @Resource
    private MockMvc mockMvc;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private EmotionDiaryMapper diaryMapper;
    @Resource
    private AiAnalysisTaskMapper taskMapper;
    @Resource
    private AiAnalysisTaskService taskService;

    /** 替换掉 MockAiChatClient，让每次分析都抛异常 */
    @MockitoBean
    private AiChatClient aiChatClient;

    private long submitDiary() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/emotion-diary")
                        .header("token", jwtUtil.generateToken(2L, 1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("moodScore", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
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
        when(aiChatClient.analyzeEmotion(anyList()))
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
        when(aiChatClient.analyzeEmotion(anyList()))
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
    }
}
