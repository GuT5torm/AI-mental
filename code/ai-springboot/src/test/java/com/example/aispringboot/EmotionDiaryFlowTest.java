package com.example.aispringboot;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aispringboot.common.JwtUtil;
import com.example.aispringboot.entity.AiAnalysisTask;
import com.example.aispringboot.entity.EmotionDiary;
import com.example.aispringboot.mapper.AiAnalysisTaskMapper;
import com.example.aispringboot.mapper.EmotionDiaryMapper;
import com.example.aispringboot.service.AiAnalysisTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 情绪日记与 AI 分析任务队列的端到端校验。
 *
 * <p>库里既有 17 条日记，全部属于 user_id=2（9 条，用户名 test）与 user_id=4（8 条，用户名 ces），
 * 另有 14 条 COMPLETED 任务。用例全部标了 @Transactional，跑完回滚，不会留下脏数据。
 *
 * <p>定时器在这里被显式关掉：AI 任务消费者会真实写库，
 * 若沿用默认配置，它可能在断言期间改掉被测数据。消费逻辑改为直接调用
 * {@link AiAnalysisTaskService#processPendingTasks()} 驱动。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.scheduler.enabled=false")
public class EmotionDiaryFlowTest {

    /** 既有日记总数，用于校验分页没有多查也没有漏查 */
    private static final int EXISTING_DIARY_TOTAL = 17;
    /** 既有日记按用户拆分：user_id=2 是 test，user_id=4 是 ces */
    private static final int EXISTING_DIARY_USER_2 = 9;
    private static final int EXISTING_DIARY_USER_4 = 8;

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

    /** 普通用户 */
    private String userToken() {
        return jwtUtil.generateToken(2L, 1);
    }

    /** 管理员，userType=2 */
    private String adminToken() {
        return jwtUtil.generateToken(1L, 2);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    private long submitDiary(Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/emotion-diary")
                        .header("token", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 提交日记_落库并生成一条PENDING任务() throws Exception {
        long id = submitDiary(Map.of(
                "diaryDate", "2026-01-05",
                "moodScore", 8,
                "dominantEmotion", "平静",
                "diaryContent", "今天把本周的用例写完了"));

        EmotionDiary diary = diaryMapper.selectById(id);
        assertNotNull(diary);
        assertEquals(2L, diary.getUserId());
        assertEquals(8, diary.getMoodScore());
        assertEquals("2026-01-05", diary.getDiaryDate().toString());

        List<AiAnalysisTask> tasks = taskMapper.selectList(
                Wrappers.lambdaQuery(AiAnalysisTask.class).eq(AiAnalysisTask::getDiaryId, id));
        assertEquals(1, tasks.size());
        assertEquals("AUTO", tasks.get(0).getTaskType());
        assertEquals("PENDING", tasks.get(0).getStatus());
        assertEquals(2, tasks.get(0).getPriority());
        assertEquals(0, tasks.get(0).getRetryCount());
        assertEquals(3, tasks.get(0).getMaxRetryCount());
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 未填日期时兜底为今天() throws Exception {
        long id = submitDiary(Map.of("moodScore", 5));
        EmotionDiary diary = diaryMapper.selectById(id);
        assertEquals(java.time.LocalDate.now(), diary.getDiaryDate());
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 未登录提交返回401() throws Exception {
        mockMvc.perform(post("/api/emotion-diary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("moodScore", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 缺少情绪评分返回400() throws Exception {
        mockMvc.perform(post("/api/emotion-diary")
                        .header("token", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("diaryContent", "没有评分"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.data").value("请选择情绪评分"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 情绪评分越界返回400() throws Exception {
        mockMvc.perform(post("/api/emotion-diary")
                        .header("token", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("moodScore", 11))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 后台分页_返回用户名与昵称() throws Exception {
        // 用自己提交的一条做基准，避免断言依赖既有数据的排序
        submitDiary(Map.of(
                "diaryDate", "2026-01-06",
                "moodScore", 7,
                "diaryContent", "校验列表行是否带上用户名"));

        MvcResult result = mockMvc.perform(get("/api/emotion-diary/admin/page")
                        .param("currentPage", "1")
                        .param("size", "50")
                        .param("userId", "2")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals(EXISTING_DIARY_USER_2 + 1, data.get("total").asInt());

        // 详情弹窗直接复用列表行，username / nickname 必须是行内字段而不是前端二次请求
        for (JsonNode row : data.get("records")) {
            assertEquals("test", row.get("username").asText());
            assertEquals("小明", row.get("nickname").asText());
            assertTrue(row.has("diaryDate"));
            assertTrue(row.has("moodScore"));
        }
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 普通用户访问后台分页返回A0301() throws Exception {
        mockMvc.perform(get("/api/emotion-diary/admin/page")
                        .header("token", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 按评分区间筛选生效() throws Exception {
        int low = countWithRange("1-3");
        int high = countWithRange("7-10");

        assertTrue(low > 0, "低分区间应有数据");
        assertTrue(high > 0, "高分区间应有数据");
        assertEquals(EXISTING_DIARY_TOTAL, low + countWithRange("4-6") + high,
                "三个区间互不重叠，合计应等于总数");
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 按用户筛选生效() throws Exception {
        assertEquals(EXISTING_DIARY_USER_2, countByUser(2));
        assertEquals(EXISTING_DIARY_USER_4, countByUser(4));
        assertEquals(EXISTING_DIARY_TOTAL, countByUser(2) + countByUser(4),
                "既有日记只分布在 2 和 4 两个用户下");
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 任务消费_回填分析结果并置为COMPLETED() throws Exception {
        long diaryId = submitDiary(Map.of(
                "moodScore", 3,
                "dominantEmotion", "焦虑",
                "emotionTriggers", "考试",
                "diaryContent", "明天考试，复习不完，很焦虑"));

        int processed = taskService.processPendingTasks();
        assertTrue(processed >= 1, "应至少消费掉刚提交的任务");

        EmotionDiary diary = diaryMapper.selectById(diaryId);
        assertNotNull(diary.getAiEmotionAnalysis(), "应回填分析结果");
        assertNotNull(diary.getAiAnalysisUpdatedAt(), "应回填分析时间");

        // 前端是 JSON.parse(row.aiEmotionAnalysis)，这里必须是合法 JSON 字符串
        JsonNode emotion = objectMapper.readTree(diary.getAiEmotionAnalysis());
        assertTrue(emotion.has("primaryEmotion"));
        assertTrue(emotion.has("emotionScore"));
        assertTrue(emotion.has("suggestion"));
        assertTrue(emotion.has("improvementSuggestions"));
        assertTrue(emotion.get("isNegative").asBoolean(), "含负面词应判为负面情绪");

        List<AiAnalysisTask> tasks = taskMapper.selectList(
                Wrappers.lambdaQuery(AiAnalysisTask.class).eq(AiAnalysisTask::getDiaryId, diaryId));
        assertEquals("COMPLETED", tasks.get(0).getStatus());
        assertNotNull(tasks.get(0).getCompletedAt());
        assertNotNull(tasks.get(0).getStartedAt());
    }

    /**
     * 删除日记后任务一并消失。
     *
     * <p>ai_analysis_task 上有 {@code fk_ai_task_diary ... ON DELETE CASCADE}，
     * 所以"日记被删、任务还在"的孤儿数据在生产库里不可能出现；Service 里仍显式
     * 删了一次任务，本用例对两者都成立。
     *
     * <p>重试路径见 {@link AiAnalysisTaskRetryTest}：库层面造不出孤儿任务，
     * 只能让模型调用抛异常来驱动。
     */
    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 删除日记_连带清理其分析任务() throws Exception {
        long diaryId = submitDiary(Map.of("moodScore", 6));

        mockMvc.perform(delete("/api/emotion-diary/admin/" + diaryId)
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        assertEquals(null, diaryMapper.selectById(diaryId), "日记应已物理删除");
        assertEquals(0, taskMapper.selectCount(
                Wrappers.lambdaQuery(AiAnalysisTask.class).eq(AiAnalysisTask::getDiaryId, diaryId)));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 删除不存在的日记返回6042() throws Exception {
        mockMvc.perform(delete("/api/emotion-diary/admin/999999")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("6042"));
    }

    private int countByUser(long userId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/emotion-diary/admin/page")
                        .param("userId", String.valueOf(userId))
                        .param("size", "50")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("total").asInt();
    }

    private int countWithRange(String range) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/emotion-diary/admin/page")
                        .param("moodScoreRange", range)
                        .param("size", "50")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("total").asInt();
    }
}
