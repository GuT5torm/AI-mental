package com.example.aispringboot;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aispringboot.common.JwtUtil;
import com.example.aispringboot.entity.AiAnalysisTask;
import com.example.aispringboot.entity.EmotionDiary;
import com.example.aispringboot.mapper.AiAnalysisTaskMapper;
import com.example.aispringboot.mapper.EmotionDiaryMapper;
import com.example.aispringboot.service.EmotionDiaryService;
import com.example.aispringboot.service.EmotionKeywordService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 咨询关键词触发情绪自动记录的校验。
 *
 * <p>seed 里预置了 12 个启用关键词（焦虑、压力大、失眠等），用例直接依赖；
 * 全部标 @Transactional 回滚，不产生脏数据。定时器关闭原因同 EmotionDiaryFlowTest。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.scheduler.enabled=false")
public class EmotionCaptureFlowTest {

    @Resource
    private EmotionKeywordService keywordService;
    @Resource
    private EmotionDiaryService diaryService;
    @Resource
    private EmotionDiaryMapper diaryMapper;
    @Resource
    private AiAnalysisTaskMapper taskMapper;
    @Resource
    private MockMvc mockMvc;
    @Resource
    private JwtUtil jwtUtil;

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 关键词命中_返回全部命中词() {
        List<String> matched = keywordService.matchKeywords("我最近压力大，晚上失眠，白天很焦虑");
        assertEquals(List.of("焦虑", "压力大", "失眠"), matched, "按词表 id 顺序返回");
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 关键词未命中_返回空列表() {
        assertTrue(keywordService.matchKeywords("今天天气不错，出去走了走").isEmpty());
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 空消息_返回空列表() {
        assertTrue(keywordService.matchKeywords(null).isEmpty());
        assertTrue(keywordService.matchKeywords("   ").isEmpty());
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 自动记录_落库字段与任务投递正确() {
        List<String> matched = keywordService.matchKeywords("最近压力大，有点撑不住了");
        assertFalse(matched.isEmpty());

        Long diaryId = diaryService.createDiaryFromChat(2L, 41L,
                "最近压力大，有点撑不住了", matched, 3);

        EmotionDiary diary = diaryMapper.selectById(diaryId);
        assertNotNull(diary);
        assertEquals(2L, diary.getUserId());
        assertEquals(EmotionDiaryService.SOURCE_CHAT, diary.getSource());
        assertEquals(41L, diary.getSourceSessionId());
        assertEquals(3, diary.getMoodScore(), "自动记录取配置默认分");
        assertEquals("最近压力大，有点撑不住了", diary.getDiaryContent());
        assertTrue(diary.getEmotionTriggers().contains("AI咨询对话触发"));
        assertTrue(diary.getEmotionTriggers().contains("压力大"));
        assertNull(diary.getDominantEmotion(), "情绪标签留给异步评估回填分析字段");
        assertNull(diary.getSleepQuality());
        assertNull(diary.getStressLevel());

        List<AiAnalysisTask> tasks = taskMapper.selectList(
                Wrappers.lambdaQuery(AiAnalysisTask.class).eq(AiAnalysisTask::getDiaryId, diaryId));
        assertEquals(1, tasks.size());
        assertEquals("AUTO", tasks.get(0).getTaskType());
        assertEquals("PENDING", tasks.get(0).getStatus());
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 同会话去重_首次记录后命中不再记录() {
        // 41/42 是 SSE 流式测试可能用到的会话号，取冷门号避免并行残留干扰
        assertFalse(diaryService.hasChatCapture(999041L), "seed 数据没有 CHAT 来源记录");

        Long diaryId = diaryService.createDiaryFromChat(2L, 999041L, "我很焦虑", List.of("焦虑"), 3);
        assertTrue(diaryService.hasChatCapture(999041L));

        // 其它会话不受影响
        assertFalse(diaryService.hasChatCapture(999042L));
        assertNotNull(diaryMapper.selectById(diaryId));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 我的记录接口_只返回本人且含CHAT来源() throws Exception {
        // user_id=2 在 seed 里已有 9 条记录
        String token = jwtUtil.generateToken(2L, 1);

        mockMvc.perform(get("/api/emotion-diary/my").header("Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.length()").value(9))
                .andExpect(jsonPath("$..userId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo(2))));

        // 事务内插一条 CHAT 记录，接口应立即可见（同一事务）
        Long diaryId = diaryService.createDiaryFromChat(2L, 999041L, "我很焦虑", List.of("焦虑"), 3);

        mockMvc.perform(get("/api/emotion-diary/my").header("Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10))
                // 日期倒序时今天的 CHAT 记录排最前
                .andExpect(jsonPath("$.data[0].id").value(diaryId.intValue()))
                .andExpect(jsonPath("$.data[0].source").value("CHAT"));

        // 其他用户看不到这条记录：ces（user_id=4）的列表长度不变
        String otherToken = jwtUtil.generateToken(4L, 1);
        mockMvc.perform(get("/api/emotion-diary/my").header("Token", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8));
    }
}
