package com.example.aispringboot;

import com.example.aispringboot.common.JwtUtil;
import com.example.aispringboot.entity.ConsultationMessage;
import com.example.aispringboot.entity.ConsultationSession;
import com.example.aispringboot.mapper.ConsultationMessageMapper;
import com.example.aispringboot.mapper.ConsultationSessionMapper;
import com.example.aispringboot.service.ConsultationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 心理咨询会话的端到端校验。
 *
 * <p>库里既有 5 个会话（id 17~21，全部属于 user_id=2）与 13 条消息。
 * 除流式对话外的用例都标了 @Transactional，跑完自动回滚；
 * 流式用例单独不标，因为它在独立线程池里执行，主事务看不到它的写入。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.scheduler.enabled=false")
public class ConsultationFlowTest {

    @Resource
    private MockMvc mockMvc;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private ConsultationSessionMapper sessionMapper;
    @Resource
    private ConsultationMessageMapper messageMapper;

    private String token(long userId) {
        return jwtUtil.generateToken(userId, 1);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 开始会话_返回带前缀的sessionId与ACTIVATE状态() throws Exception {
        Map<String, Object> body = Map.of("sessionTitle", "测试会话");

        mockMvc.perform(post("/api/psychological-chat/session/start")
                        .header("token", token(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.status").value("ACTIVATE"))
                .andExpect(jsonPath("$.data.sessionTitle").value("测试会话"))
                .andExpect(jsonPath("$.data.sessionId").value(
                        org.hamcrest.Matchers.startsWith("session_")));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 未登录访问会话列表返回401() throws Exception {
        mockMvc.perform(get("/api/psychological-chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 会话列表_只返回本人会话并带派生字段() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/psychological-chat/sessions")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .header("token", token(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertTrue(data.get("total").asInt() >= 5, "user 2 至少应有 5 个既有会话");
        assertTrue(data.get("records").size() > 0);

        JsonNode first = data.get("records").get(0);
        assertTrue(first.has("sessionTitle"));
        assertTrue(first.has("messageCount"));
        assertTrue(first.has("durationMinutes"));
        assertTrue(first.has("lastMessageContent"));

        // 会话 17 有 6 条消息，列表里应能取到
        boolean found = false;
        for (JsonNode n : data.get("records")) {
            if (n.get("id").asLong() == 17L) {
                found = true;
                assertEquals(6, n.get("messageCount").asInt());
            }
        }
        assertTrue(found, "应包含既有会话 17");
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 消息列表_按升序返回且不带他人消息() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/psychological-chat/sessions/17/messages")
                        .header("token", token(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andReturn();

        JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals(6, list.size());
        assertEquals(1, list.get(0).get("senderType").asInt());
        assertEquals(2, list.get(1).get("senderType").asInt());
        assertTrue(list.get(0).get("id").asLong() < list.get(1).get("id").asLong(), "消息应按 id 升序");
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 跨用户访问会话返回6041() throws Exception {
        mockMvc.perform(get("/api/psychological-chat/sessions/17/messages")
                        .header("token", token(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("6041"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void sessionId兼容带前缀与纯数字两种写法() throws Exception {
        mockMvc.perform(get("/api/psychological-chat/sessions/session_17/messages")
                        .header("token", token(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 非法sessionId返回4002() throws Exception {
        mockMvc.perform(get("/api/psychological-chat/sessions/session_abc/messages")
                        .header("token", token(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("4002"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 既有会话的情绪分析可直接回读() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/psychological-chat/session/session_17/emotion")
                        .header("token", token(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andReturn();

        JsonNode emotion = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertTrue(emotion.has("primaryEmotion"), "应返回既有 jsonb 里的 primaryEmotion");
        assertTrue(emotion.has("emotionScore"));
        assertTrue(emotion.has("improvementSuggestions"));
        assertTrue(emotion.get("improvementSuggestions").isArray());
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 删除会话_级联删除其消息() throws Exception {
        // 会话 18 只有 1 条消息
        mockMvc.perform(delete("/api/psychological-chat/sessions/18")
                        .header("token", token(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        LambdaQueryWrapper<ConsultationMessage> wrapper = Wrappers.lambdaQuery(ConsultationMessage.class)
                .eq(ConsultationMessage::getSessionId, 18L);
        assertEquals(0L, messageMapper.selectCount(wrapper));
    }

    /**
     * 流式对话：走真实路径（控制器 → 线程池 → SSE）。
     *
     * <p>这里没有标 @Transactional，因为推流在独立线程里落库，
     * 主事务回滚不了它；改用例自己在 finally 里清理。
     */
    @Test
    public void 流式对话_推送正文块与done事件并落库() throws Exception {
        MvcResult start = mockMvc.perform(post("/api/psychological-chat/session/start")
                        .header("token", token(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sessionTitle", "流式用例"))))
                .andExpect(status().isOk())
                .andReturn();
        String sessionId = objectMapper.readTree(start.getResponse().getContentAsString())
                .get("data").get("sessionId").asText();
        long id = ConsultationService.parseSessionId(sessionId);

        try {
            MvcResult stream = mockMvc.perform(post("/api/psychological-chat/stream")
                            .header("token", token(2L))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("sessionId", sessionId, "userMessage", "我最近压力很大"))))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            // 等待异步线程推完
            stream.getAsyncResult(15000);

            String body = stream.getResponse().getContentAsString();
            assertTrue(body.contains("event:done"), "应推送命名事件 done，实际=" + body);
            assertTrue(body.contains("\"code\":\"200\""), "应推送正文帧");

            // 用户消息与 AI 消息都应落库
            List<ConsultationMessage> messages = messageMapper.selectList(
                    Wrappers.lambdaQuery(ConsultationMessage.class)
                            .eq(ConsultationMessage::getSessionId, id)
                            .orderByAsc(ConsultationMessage::getId));
            assertEquals(2, messages.size());
            assertEquals(1, messages.get(0).getSenderType());
            assertEquals("我最近压力很大", messages.get(0).getContent());
            assertEquals(2, messages.get(1).getSenderType());
            assertNotNull(messages.get(1).getAiModel(), "AI 消息应记录模型名");
            assertTrue(messages.get(1).getContent().contains("Mock"), "AI 回复应落库完整内容");

            // 情绪分析应在 done 之前完成
            ConsultationSession session = sessionMapper.selectById(id);
            assertNotNull(session.getLastEmotionAnalysis(), "应回写情绪分析结果");
            JsonNode emotion = objectMapper.readTree(session.getLastEmotionAnalysis());
            assertEquals(true, emotion.get("isNegative").asBoolean(), "含负面词应判为负面");
        } finally {
            messageMapper.deleteBySessionId(id);
            sessionMapper.deleteById(id);
        }
    }
}
