package com.example.aispringboot;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aispringboot.common.AiKeyCipher;
import com.example.aispringboot.common.JwtUtil;
import com.example.aispringboot.entity.ConsultationMessage;
import com.example.aispringboot.entity.ConsultationSession;
import com.example.aispringboot.entity.UserAiConfig;
import com.example.aispringboot.mapper.ConsultationMessageMapper;
import com.example.aispringboot.mapper.ConsultationSessionMapper;
import com.example.aispringboot.mapper.UserAiConfigMapper;
import com.example.aispringboot.service.ConsultationService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户自配 API Key 的端到端校验。
 *
 * <p>本类单独指定了 {@code app.ai.config-secret}，会启动独立测试上下文；
 * 其余用例走 MockMvc + 事务回滚，流式回归用例不标事务（推流在独立线程落库），
 * 由用例自己在 finally 里清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.ai.scheduler.enabled=false",
        "app.ai.config-secret=test-secret-0123456789",
        "app.emotion-capture.enabled=false"
})
public class AiConfigFlowTest {

    private static final String PLAIN_KEY = "sk-flow-secret-1234567890";
    private static final String MASKED_KEY = "sk-****7890";

    @Resource
    private MockMvc mockMvc;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private UserAiConfigMapper configMapper;
    @Resource
    private ConsultationSessionMapper sessionMapper;
    @Resource
    private ConsultationMessageMapper messageMapper;
    @Resource
    private AiKeyCipher cipher;

    private String token(long userId) {
        return jwtUtil.generateToken(userId, 1);
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    private MvcResult putConfig(long userId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/ai-config")
                        .header("token", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 未登录访问AI配置返回401() throws Exception {
        mockMvc.perform(get("/api/ai-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 初始状态_未配置时只返回configured为false() throws Exception {
        mockMvc.perform(get("/api/ai-config").header("token", token(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 保存Key_返回掩码且库里只落密文() throws Exception {
        MvcResult result = putConfig(1L, Map.of("apiKey", PLAIN_KEY));

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        assertEquals("200", objectMapper.readTree(body).get("code").asText());
        assertTrue(data.get("configured").asBoolean());
        assertEquals(MASKED_KEY, data.get("maskedKey").asText());
        assertFalse(body.contains(PLAIN_KEY), "响应里不允许出现完整 Key 明文");

        // 库里应是 iv:密文 形式的 AES-GCM 密文，而非明文
        UserAiConfig saved = configMapper.selectOne(Wrappers.lambdaQuery(UserAiConfig.class)
                .eq(UserAiConfig::getUserId, 1L));
        assertNotNull(saved);
        assertNotEquals(PLAIN_KEY, saved.getApiKeyCipher());
        assertTrue(saved.getApiKeyCipher().contains(":"), "密文应为 Base64(iv):Base64(密文) 格式");
        assertEquals(PLAIN_KEY, cipher.decrypt(saved.getApiKeyCipher()), "密文应可用主密钥还原");
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void baseUrl指向内网地址时保存返回4002() throws Exception {
        putConfig(1L, Map.of("apiKey", PLAIN_KEY, "baseUrl", "http://127.0.0.1:8080"))
                .getResponse().getContentAsString();
        mockMvc.perform(put("/api/ai-config")
                        .header("token", token(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("apiKey", PLAIN_KEY, "baseUrl", "http://192.168.1.10:8000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("4002"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 连通性校验_内网地址直接拒绝不发真实请求() throws Exception {
        mockMvc.perform(post("/api/ai-config/test")
                        .header("token", token(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("apiKey", "sk-xxx", "baseUrl", "http://127.0.0.1:9999"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("4002"));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 跨用户隔离_各人只能看到自己的配置() throws Exception {
        putConfig(2L, Map.of("apiKey", PLAIN_KEY, "model", "deepseek-reasoner"));

        mockMvc.perform(get("/api/ai-config").header("token", token(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false));

        MvcResult own = mockMvc.perform(get("/api/ai-config").header("token", token(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.model").value("deepseek-reasoner"))
                .andReturn();
        assertFalse(own.getResponse().getContentAsString().contains(PLAIN_KEY));
    }

    @Test
    @Transactional(rollbackFor = Exception.class)
    public void 删除配置后恢复为未配置状态() throws Exception {
        putConfig(2L, Map.of("apiKey", PLAIN_KEY));

        mockMvc.perform(delete("/api/ai-config").header("token", token(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        mockMvc.perform(get("/api/ai-config").header("token", token(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false));
    }

    /**
     * 用户已配置自研 Key 时流式对话自动切换到真实模型链路。
     *
     * <p>保存即生效：resolveOptions 非空时路由客户端走 DeepSeekAiChatClient，
     * 不再回到 mock。用 JDK 假端点替代真实服务（测试直插加密配置绕过 SSRF 校验），
     * 断言回复确实来自自配端点。不标事务：推流在独立线程里落库，改用 finally 自清理。
     */
    @Test
    public void 用户已配置Key时流式对话自动走自配端点() throws Exception {
        try (FakeOpenAiServer fake = new FakeOpenAiServer()) {
            // 先清掉可能的残留，再自提交插入指向假端点的用户配置
            configMapper.delete(Wrappers.lambdaQuery(UserAiConfig.class)
                    .eq(UserAiConfig::getUserId, 2L));
            UserAiConfig config = new UserAiConfig();
            config.setUserId(2L);
            config.setApiKeyCipher(cipher.encrypt("sk-stream-flow-key"));
            config.setBaseUrl(fake.url());
            config.setModel("deepseek-chat");
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            configMapper.insert(config);

            MvcResult start = mockMvc.perform(post("/api/psychological-chat/session/start")
                            .header("token", token(2L))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("sessionTitle", "自配Key流式用例"))))
                    .andExpect(status().isOk())
                    .andReturn();
            String sessionId = objectMapper.readTree(start.getResponse().getContentAsString())
                    .get("data").get("sessionId").asText();
            long id = ConsultationService.parseSessionId(sessionId);

            try {
                MvcResult stream = mockMvc.perform(post("/api/psychological-chat/stream")
                                .header("token", token(2L))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("sessionId", sessionId, "userMessage", "我有点焦虑"))))
                        .andExpect(request().asyncStarted())
                        .andReturn();
                stream.getAsyncResult(15000);

                String body = stream.getResponse().getContentAsString();

                assertTrue(body.contains("event:done"), "应推送命名事件 done，实际=" + body);
                assertTrue(body.contains("\"code\":\"200\""), "应推送正文帧");

                List<ConsultationMessage> messages = messageMapper.selectList(
                        Wrappers.lambdaQuery(ConsultationMessage.class)
                                .eq(ConsultationMessage::getSessionId, id)
                                .orderByAsc(ConsultationMessage::getId));
                assertEquals(2, messages.size(), "用户消息与 AI 消息都应落库");
                assertTrue(messages.get(1).getContent().contains(FakeOpenAiServer.REPLY_TEXT),
                        "配置 Key 后应走自配端点的真实回复，实际=" + messages.get(1).getContent());
                assertTrue(fake.requestCount() > 0, "假端点应收到真实请求");

                // 配置在对话后仍在，且以掩码形式可见
                mockMvc.perform(get("/api/ai-config").header("token", token(2L)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.configured").value(true))
                        .andExpect(jsonPath("$.data.maskedKey").value("sk-****-key"));
            } finally {
                messageMapper.deleteBySessionId(id);
                sessionMapper.deleteById(id);
                configMapper.delete(Wrappers.lambdaQuery(UserAiConfig.class)
                        .eq(UserAiConfig::getUserId, 2L));
            }
        }
    }
}
